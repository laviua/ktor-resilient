package ua.com.lavi.ktor.resilient.examples

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import ua.com.lavi.ktor.resilient.ResilientCIOHttpClient
import java.time.Duration

data class Ip(val ip: String)

/**
 * The class provides retry and time-limiter functionalities to the underlying HTTP client calls.
 */
class ReorderedResilient : ResilientCIOHttpClient() {

    override suspend fun <T> resilient(block: suspend HttpClient.() -> T): T {
        return retry {
            client().block()
        }
    }
}

fun main(args: Array<String>) {

    val retryConfig = RetryConfig.custom<HttpResponse>()
        .maxAttempts(3)
        .retryOnResult { response: HttpResponse -> response.status.value == 500 }
        .waitDuration(Duration.ofMillis(100))
        .build()

    val httpClient = ReorderedResilient()
        .withRetry(Retry.of("test", retryConfig))

    runBlocking {
        val ip: Ip = httpClient.get("https://api.ipify.org?format=json").body()
        print("My ip: ${ip.ip}")
    }
}