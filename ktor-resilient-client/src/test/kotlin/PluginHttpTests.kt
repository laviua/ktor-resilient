package ua.com.lavi.ktor.resilient.client

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.github.resilience4j.retry.MaxRetriesExceededException
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ua.com.lavi.ktor.resilient.client.plugins.*
import java.net.ConnectException
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException

@WireMockTest
class PluginHttpTests {

    @BeforeEach
    fun setup(wireMock: WireMockRuntimeInfo) {
        stubFor(get("/ok").willReturn(ok().withBody("ok")))
        stubFor(get("/delayed-200").willReturn(ok().withBody("ok").withFixedDelay(200)))
        stubFor(get("/error").willReturn(serverError().withBody("server error")))
    }

    @Test
    fun shouldRetry(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val retryModule = Retry.of("test", RetryConfig.custom<HttpClientCall>()
            .maxAttempts(2)
            .intervalFunction(IntervalFunction.of(Duration.ofMillis(50)))
            .retryOnResult { httpClientCall: HttpClientCall -> httpClientCall.response.status.value == 500 }
            .failAfterMaxAttempts(true)
            .build())

        val httpClient = HttpClient(CIO) {
            install(Logging)
            install(RetryPlugin) {
                retry = retryModule
            }
        }

        retryModule.metrics.numberOfFailedCallsWithRetryAttempt shouldBe 0
        shouldThrow<MaxRetriesExceededException> {
            httpClient.get("${wireMock.httpBaseUrl}/error")
        }
        retryModule.metrics.numberOfFailedCallsWithRetryAttempt shouldBe 2
        httpClient.close()
    }

    @Test
    fun shouldNotRetry(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val retryModule = Retry.of("test", RetryConfig.custom<HttpClientCall>()
            .maxAttempts(2)
            .intervalFunction(IntervalFunction.of(Duration.ofMillis(50)))
            .retryOnResult { httpClientCall: HttpClientCall -> httpClientCall.response.status.value == 500 }
            .failAfterMaxAttempts(true)
            .build())

        val httpClient = HttpClient(CIO) {
            install(Logging)
            install(RetryPlugin) {
                retry = retryModule
            }
        }
        retryModule.metrics.numberOfFailedCallsWithRetryAttempt shouldBe 0
        httpClient.get("${wireMock.httpBaseUrl}/ok")
        retryModule.metrics.numberOfFailedCallsWithRetryAttempt shouldBe 0
        httpClient.close()
    }


    @Test
    fun shouldSuccessNoTimeout(wireMock: WireMockRuntimeInfo): Unit = runBlocking {

        val httpClient = HttpClient(CIO) {
            install(Logging)
            install(TimeLimiterPlugin) {
                timeLimiter = TimeLimiter.of(
                    "test", TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.of(1, ChronoUnit.SECONDS))
                        .build()
                )
            }
        }
        val start = System.currentTimeMillis()
        val response = httpClient.get("${wireMock.httpBaseUrl}/ok")
        val end = System.currentTimeMillis()
        println("Request took: ${end - start} ms")
        response.status.value shouldBe 200
        httpClient.close()
    }

    @Test
    fun shouldFailBecauseOfTheTimeout(wireMock: WireMockRuntimeInfo): Unit = runBlocking {

        val httpClient = HttpClient(CIO) {
            install(Logging)
            install(TimeLimiterPlugin) {
                timeLimiter = TimeLimiter.of(
                    "test", TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.of(50, ChronoUnit.MILLIS))
                        .build()
                )
            }
        }
        shouldThrow<TimeoutException> {
            httpClient.get("${wireMock.httpBaseUrl}/delayed-200")
        }
        httpClient.close()
    }

    @Test
    fun shouldRatelimit(wireMock: WireMockRuntimeInfo): Unit = runBlocking {

        val httpClient = HttpClient(CIO) {
            install(Logging)
            install(RateLimiterPlugin) {
                rateLimiter = RateLimiter.of(
                    "test", RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofMillis(250))
                        .timeoutDuration(Duration.ofMillis(25))
                        .build()
                )
            }
        }
        shouldThrow<RequestNotPermitted> {
            httpClient.get("${wireMock.httpBaseUrl}/ok")
            httpClient.get("${wireMock.httpBaseUrl}/ok")
        }
        httpClient.close()
    }

    @Test
    fun shouldOkRatelimit(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val httpClient = HttpClient(CIO) {
            install(Logging)
            install(RateLimiterPlugin) {
                rateLimiter = RateLimiter.of(
                    "test", RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofMillis(250))
                        .timeoutDuration(Duration.ofMillis(25))
                        .build()
                )
            }
        }
        httpClient.get("${wireMock.httpBaseUrl}/ok")
        Thread.sleep(250)
        httpClient.get("${wireMock.httpBaseUrl}/ok")
        httpClient.close()
    }

    @Test
    fun shouldSuccessCircuitBreaker(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val cb = CircuitBreaker.of(
            "test", CircuitBreakerConfig.custom()
                .failureRateThreshold(100.0f) // Open circuit on first failure
                .minimumNumberOfCalls(1)
                .slidingWindowSize(1)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .build()
        )

        val httpClient = HttpClient(CIO) {
            install(Logging)
            install(CircuitBreakerPlugin) {
                circuitBreaker = cb
            }
        }
        val response = httpClient.get("${wireMock.httpBaseUrl}/ok")
        response.status.value shouldBe 200
        cb.state shouldBe CircuitBreaker.State.CLOSED
        cb.metrics.numberOfFailedCalls shouldBe 0
        httpClient.close()
    }

    @Test
    fun shouldFailCircuitBreaker(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val cb = CircuitBreaker.of(
            "test", CircuitBreakerConfig.custom()
                .failureRateThreshold(100.0f) // Open circuit on first failure
                .minimumNumberOfCalls(1)
                .slidingWindowSize(1)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .build()
        )

        val httpClient = HttpClient(MockEngine { throw ConnectException() }) {
            install(Logging)
            install(CircuitBreakerPlugin) {
                circuitBreaker = cb
            }
        }
        shouldThrow<ConnectException> {
            httpClient.get("${wireMock.httpBaseUrl}/error")
        }
        cb.state shouldBe CircuitBreaker.State.OPEN
        cb.metrics.numberOfFailedCalls shouldBe 1

        shouldThrow<CallNotPermittedException> {
            httpClient.get("${wireMock.httpBaseUrl}/ok")
        }
        httpClient.close()
    }

    @Test
    fun shouldSuccessTestBulkhead(wireMock: WireMockRuntimeInfo) {

        val httpClient = HttpClient(CIO) {
            install(Logging)
            install(BulkheadPlugin) {
                bulkhead = Bulkhead.of(
                    "test", BulkheadConfig.custom()
                        .maxConcurrentCalls(2)
                        .maxWaitDuration(Duration.ZERO)
                        .writableStackTraceEnabled(false)
                        .fairCallHandlingStrategyEnabled(true)
                        .build()
                )
            }
        }

        runBlocking {
            val deferred = async {
                httpClient.get("${wireMock.httpBaseUrl}/ok")
            }
            httpClient.get("${wireMock.httpBaseUrl}/ok")
            deferred.await()
        }
        httpClient.close()
    }

    @Test
    fun shouldFailTestBulkhead(wireMock: WireMockRuntimeInfo) {

        val httpClient = HttpClient(CIO) {
            install(Logging)
            install(BulkheadPlugin) {
                bulkhead = Bulkhead.of(
                    "test", BulkheadConfig.custom()
                        .maxConcurrentCalls(1)
                        .maxWaitDuration(Duration.ZERO)
                        .writableStackTraceEnabled(false)
                        .fairCallHandlingStrategyEnabled(true)
                        .build()
                )
            }
        }

        shouldThrow<BulkheadFullException> {
            runBlocking {
                val deferred = async {
                    httpClient.get("${wireMock.httpBaseUrl}/delayed-200")
                }
                httpClient.get("${wireMock.httpBaseUrl}/delayed-200")
                deferred.await()
            }
        }
        httpClient.close()
    }

    @Test
    fun complexPluginChain(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val cb = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f) // Open circuit on first failure
            .minimumNumberOfCalls(1)
            .slidingWindowSize(1)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .build()
        )

        val retryModule = Retry.of("test", RetryConfig.custom<HttpClientCall>()
            .maxAttempts(5)
            .intervalFunction(IntervalFunction.of(Duration.ofMillis(10)))
            .retryOnResult { httpClientCall: HttpClientCall -> httpClientCall.response.status.value == 500 }
            .failAfterMaxAttempts(true)
            .build())

        val httpClient = HttpClient(MockEngine { throw ConnectException() }) {
            install(Logging)
            install(RetryPlugin) { retry = retryModule }
            install(CircuitBreakerPlugin) { circuitBreaker = cb }
        }

        shouldThrow<CallNotPermittedException> {
            httpClient.get("${wireMock.httpBaseUrl}/does not matter")
        }
        cb.state shouldBe CircuitBreaker.State.OPEN
        retryModule.metrics.numberOfFailedCallsWithRetryAttempt shouldBe 1
        httpClient.close()
    }
}