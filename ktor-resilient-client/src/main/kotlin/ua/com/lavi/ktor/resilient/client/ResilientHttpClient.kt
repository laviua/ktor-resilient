package ua.com.lavi.ktor.resilient.client

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.kotlin.bulkhead.executeSuspendFunction
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.kotlin.timelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.timelimiter.TimeLimiter
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * The recommended order is to first apply the time limiter, followed by the circuit breaker, and finally the retry.
 * This ensures that the time limiter can limit the amount of time that a request can take,
 * the circuit breaker can trip if the requests are consistently taking too long, and finally the retry can provide an additional layer of reliability.
 */
open class ResilientHttpClient(
    private var client: HttpClient = HttpClient(),
    private var retry: Retry = Retry.ofDefaults("default"),
    private var rateLimiter: RateLimiter = RateLimiter.ofDefaults("default"),
    private var timeLimiter: TimeLimiter = TimeLimiter.ofDefaults("default"), // default timeout is 1 second
    private var circuitBreaker: CircuitBreaker = CircuitBreaker.ofDefaults("default"),
    private var bulkHead: Bulkhead = Bulkhead.ofDefaults("default")) {

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        return resilient {
            get(url) {
                headers.forEach { (key, value) -> header(key, value) }
            }
        }
    }

    suspend fun post(url: String, body: Any? = null, headers: Map<String, String> = emptyMap()): HttpResponse {
        return resilient {
            post(url) {
                headers.forEach { (key, value) -> header(key, value) }
                if (body != null) setBody(body)
            }
        }
    }

    suspend fun put(url: String, body: Any? = null, headers: Map<String, String> = emptyMap()): HttpResponse {
        return resilient {
            put(url) {
                headers.forEach { (key, value) -> header(key, value) }
                if (body != null) setBody(body)
            }
        }
    }

    suspend fun patch(url: String, body: Any? = null, headers: Map<String, String> = emptyMap()): HttpResponse {
        return resilient {
            patch(url) {
                headers.forEach { (key, value) -> header(key, value) }
                if (body != null) setBody(body)
            }
        }
    }

    suspend fun head(url: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        return resilient {
            head(url) {
                headers.forEach { (key, value) -> header(key, value) }
            }
        }
    }

    suspend fun options(url: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        return resilient {
            options(url) {
                headers.forEach { (key, value) -> header(key, value) }
            }
        }
    }

    suspend fun delete(url: String, body: Any? = null, headers: Map<String, String> = emptyMap()): HttpResponse {
        return resilient {
            delete(url) {
                headers.forEach { (key, value) -> header(key, value) }
                if (body != null) setBody(body)
            }
        }
    }


    /**
     * This method is used to wrap a block of code executed with an HttpClient instance, providing multiple error handling mechanisms to the code block.
     * The method makes use of three error handling mechanisms: time limiting, circuit breaking, and retrying.
     *
     * The Resilience4j Aspects order is following: Retry ( CircuitBreaker ( RateLimiter ( TimeLimiter ( Bulkhead ( Function ) ) ) ) )
     * First Bulkhead creates a threadpool. Then TimeLimiter can limit the time of the threads.
     * RateLimiter limits the number of calls on that function for a configurable time window. Any exceptions thrown by TimeLimiter or RateLimiter will be recorded by CircuitBreaker. Then retry will be executed.
     * @param block: a suspend lambda expression representing the block of code to be executed with an HttpClient instance. The lambda expression should take an HttpClient instance as receiver and return a value of type T.
     * @return: The method returns the result of the code block after it has been executed, with the error handling mechanisms applied. The result is of type T.
     */
    open suspend fun <T> resilient(block: suspend HttpClient.() -> T): T {
        return retry {
            circuitBreaker {
                rateLimiter {
                    timeLimiter {
                        bulkHead {
                            client.block()
                        }
                    }
                }
            }
        }
    }

    suspend fun <T> retry(block: suspend () -> T): T {
        return retry.executeSuspendFunction(block)
    }

    suspend fun <T> circuitBreaker(block: suspend () -> T): T {
        return try {
            circuitBreaker.executeSuspendFunction(block)
        } catch (e: CallNotPermittedException) {
            throw e
        }
    }

    suspend fun <T> rateLimiter(block: suspend () -> T): T {
        return rateLimiter.executeSuspendFunction(block)
    }

    suspend fun <T> timeLimiter(block: suspend () -> T): T {
        return timeLimiter.executeSuspendFunction(block)
    }

    suspend fun <T> bulkHead(block: suspend () -> T): T {
        return bulkHead.executeSuspendFunction(block)
    }

    fun client(): HttpClient {
        return client
    }

    fun withClient(httpClient: HttpClient): ResilientHttpClient {
        this.client = httpClient
        return this
    }

    fun withRetry(retry: Retry): ResilientHttpClient {
        this.retry = retry
        return this
    }

    fun withCircuitBreaker(circuitBreaker: CircuitBreaker): ResilientHttpClient {
        this.circuitBreaker = circuitBreaker
        return this
    }

    fun withRateLimiter(rateLimiter: RateLimiter): ResilientHttpClient {
        this.rateLimiter = rateLimiter
        return this
    }

    fun withTimelimiter(timeLimiter: TimeLimiter): ResilientHttpClient {
        this.timeLimiter = timeLimiter
        return this
    }

    fun withBulkHead(bulkHead: Bulkhead): ResilientHttpClient {
        this.bulkHead = bulkHead
        return this
    }

}
