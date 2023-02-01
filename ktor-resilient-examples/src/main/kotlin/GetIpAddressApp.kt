package ua.com.lavi.ktor.resilient.examples

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import ua.com.lavi.ktor.resilient.client.ResilientClient
import ua.com.lavi.ktor.resilient.jackson.ObjectMapperFactory
import java.time.Duration

data class Ip(val ip: String)

/**
 * The class provides retry and time-limiter functionalities to the underlying HTTP client calls.
 */
class CustomResilientClient : ResilientClient(
    HttpClient(CIO) {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(objectMapper = ObjectMapperFactory.objectMapper))
        }
    }
) {

    override suspend fun <T> resilient(block: suspend HttpClient.() -> T): T {
        return retry {
            httpClient().block()
        }
    }
}

fun main(args: Array<String>) {

    val retryConfig = RetryConfig.custom<HttpResponse>()
        .maxAttempts(3)
        .retryOnResult { response: HttpResponse -> response.status.value == 500 }
        .waitDuration(Duration.ofMillis(100))
        .build()

    val resilientClient = CustomResilientClient()
        .withRetry(Retry.of("test", retryConfig))

    runBlocking {
        //get
        val ip: Ip = resilientClient.get(url = "https://api.ipify.org?format=json").body()
        print("My ip: ${ip.ip}")

        //send form with the cookies
        val form = mapOf("a" to "b", "c" to "d")
        val customHeaders = mapOf("X-Custom" to "aaa")
        val cookie = Cookie(name = "a", value = "b")
        resilientClient.postForm(url = "https://formtestktor.requestcatcher.com/", form = form, headers = customHeaders, cookie=cookie)

    }
}