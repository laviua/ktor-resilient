package ua.com.lavi.ktor.resilient.examples

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.timelimiter.TimeLimiter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import ua.com.lavi.ktor.resilient.client.ResilientClient
import ua.com.lavi.ktor.resilient.jackson.ObjectMapperFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException


class ResilientHttpClientResilientTests {

    companion object {

        private val wireMockServer: WireMockServer = WireMockServer(
            WireMockConfiguration.options().port(9700).extensions(ResponseTemplateTransformer(true))
        )

        @JvmStatic
        @BeforeAll
        fun setup() {
            wireMockServer.start()

            wireMockServer.stubFor(
                get(urlEqualTo("/empty")).willReturn(
                    aResponse()
                        .withStatus(200)
                )
            )

            wireMockServer.stubFor(
                get(urlEqualTo("/delayed")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withFixedDelay(20)
                )
            )

            wireMockServer.stubFor(
                get(urlEqualTo("/error")).willReturn(
                    aResponse()
                        .withStatus(500)
                )
            )
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            wireMockServer.stop()
        }
    }

    private val engine = HttpClient(CIO) {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(objectMapper = ObjectMapperFactory.objectMapper))
        }
    }

    @Test
    fun shouldFailBecauseOfTimeout(): Unit = runBlocking {
        val httpClient = ResilientClient(httpClient = engine, timeLimiter = TimeLimiter.of(Duration.of(10, ChronoUnit.MILLIS)))
        shouldThrow<TimeoutException> {
            httpClient.get("http://127.0.0.1:9700/delayed")
        }
    }

    @Test
    fun shouldNotFailBecauseOfTimeout(): Unit = runBlocking {
        val httpClient = ResilientClient(httpClient = engine, timeLimiter = TimeLimiter.of(Duration.of(1000, ChronoUnit.MILLIS)))
        httpClient.get("http://127.0.0.1:9700/delayed").status shouldBe HttpStatusCode.OK
    }

    @Test
    fun shouldRetryBecauseOfTheError(): Unit = runBlocking {
        val config = RetryConfig.custom<HttpResponse>()
            .maxAttempts(5)
            .retryOnResult { response: HttpResponse -> response.status.value == 500 }
            .retryExceptions(Exception::class.java)
            .waitDuration(Duration.ofMillis(1))
            .build()

        val httpClient = ResilientClient(httpClient = engine, retry = Retry.of("test", config))
        val response = httpClient.get("http://127.0.0.1:9700/error")
        response.status shouldBe HttpStatusCode.InternalServerError

        wireMockServer.verify(5, getRequestedFor(urlEqualTo("/error")))
    }

    @Test
    fun shouldIgnoreTimeLimiterBecauseOfCustomResilient(): Unit = runBlocking {

        class ReorderedResilient : ResilientClient(httpClient = engine) {

            override suspend fun <T> resilient(block: suspend HttpClient.() -> T): T {
                return retry {
                    httpClient().block()
                }
            }
        }

        val retryConfig = RetryConfig.custom<HttpResponse>()
            .maxAttempts(3)
            .retryOnResult { response: HttpResponse -> response.status.value == 500 }
            .waitDuration(Duration.ofMillis(100))
            .build()

        val httpClient = ReorderedResilient()
            .withRetry(Retry.of("test", retryConfig))
            .withTimelimiter(TimeLimiter.of(Duration.of(10, ChronoUnit.MILLIS)))

        httpClient.get("http://127.0.0.1:9700/delayed").status shouldBe HttpStatusCode.OK
    }

    @Test
    fun noResilienceTest(): Unit = runBlocking {
        class NoResilience : ResilientClient(httpClient = engine) {
            override suspend fun <T> resilient(block: suspend HttpClient.() -> T): T {
                return httpClient().block()
            }
        }

        val httpClient = NoResilience()
            .withRetry(mockk())
            .withTimelimiter(mockk())
            .withCircuitBreaker(mockk())
            .withRateLimiter(mockk())
            .withBulkHead(mockk())

        httpClient.get("http://127.0.0.1:9700/empty").status shouldBe HttpStatusCode.OK
    }
}