package ua.com.lavi.ktor.resilient.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import ua.com.lavi.ktor.resilient.client.plugins.CircuitBreakerPlugin
import ua.com.lavi.ktor.resilient.client.plugins.RateLimiterPlugin
import ua.com.lavi.ktor.resilient.client.plugins.TimeLimiterPlugin
import java.net.ConnectException
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException

class PluginHttpTests {

    companion object {

        private val wireMockServer: WireMockServer = WireMockServer(
            WireMockConfiguration.options().port(9700).extensions(ResponseTemplateTransformer(true))
        )

        @JvmStatic
        @BeforeAll
        fun setup() {
            wireMockServer.start()

            wireMockServer.stubFor(
                WireMock.get(WireMock.urlEqualTo("/ok")).willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                )
            )

            wireMockServer.stubFor(
                WireMock.get(WireMock.urlEqualTo("/error")).willReturn(
                    WireMock.aResponse()
                        .withStatus(500)
                        .withFixedDelay(20)
                )
            )
            wireMockServer.stubFor(
                WireMock.get(WireMock.urlEqualTo("/delayed")).willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withFixedDelay(200)
                )
            )
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            wireMockServer.stop()
        }
    }

    @Test
    fun shouldSuccessNoTimeout(): Unit = runBlocking {

        val httpClient = HttpClient(CIO) {
            install(TimeLimiterPlugin) {
                timeLimiter = TimeLimiter.of("test", timeLimiterConfig())
            }
        }
        val response = httpClient.get("http://127.0.0.1:9700/ok")
        response.status.value shouldBe 200
    }

    @Test
    fun shouldFailBecauseOfTheTimeout(): Unit = runBlocking {

        val httpClient = HttpClient(CIO) {
            install(TimeLimiterPlugin) {
                timeLimiter = TimeLimiter.of("test", timeLimiterConfig())
            }
        }
        shouldThrow<TimeoutException> {
            httpClient.get("http://127.0.0.1:9700/delayed")
        }
    }

    @Test
    fun shouldRatelimit(): Unit = runBlocking {

        val httpClient = HttpClient(CIO) {
            install(RateLimiterPlugin) {
                rateLimiter = RateLimiter.of("test", rateLimiterConfig())
            }
        }
        shouldThrow<RequestNotPermitted> {
            httpClient.get("http://127.0.0.1:9700/ok")
            httpClient.get("http://127.0.0.1:9700/ok")
        }
    }

    @Test
    fun shouldOkRatelimit(): Unit = runBlocking {
        val httpClient = HttpClient(CIO) {

            install(RateLimiterPlugin) {
                rateLimiter = RateLimiter.of("test", rateLimiterConfig())
            }
        }
        httpClient.get("http://127.0.0.1:9700/ok")
        Thread.sleep(250)
        httpClient.get("http://127.0.0.1:9700/ok")
    }

    @Test
    fun shouldSuccessCircuitBreaker(): Unit = runBlocking {
        val cb = CircuitBreaker.of("test", circuitBreakerConfig())

        val httpClient = HttpClient(CIO) {
            install(CircuitBreakerPlugin) {
                circuitBreaker = cb
            }
        }
        val response = httpClient.get("http://127.0.0.1:9700/ok")
        response.status.value shouldBe 200
        cb.state shouldBe CircuitBreaker.State.CLOSED
        cb.metrics.numberOfFailedCalls shouldBe 0
    }

    @Test
    fun shouldFailCircuitBreaker(): Unit = runBlocking {
        val cb = CircuitBreaker.of("test", circuitBreakerConfig())

        val httpClient = HttpClient(MockEngine { throw ConnectException() }) {
            install(CircuitBreakerPlugin) {
                circuitBreaker = cb
            }
        }
        shouldThrow<ConnectException> {
            httpClient.get("http://127.0.0.1:9700/error")
        }
        cb.state shouldBe CircuitBreaker.State.OPEN
        cb.metrics.numberOfFailedCalls shouldBe 1

        shouldThrow<CallNotPermittedException> {
            httpClient.get("http://127.0.0.1:9700/ok")
        }
    }


    private fun timeLimiterConfig(): TimeLimiterConfig = TimeLimiterConfig.custom()
        .timeoutDuration(Duration.of(50, ChronoUnit.MILLIS))
        .build()

    private fun rateLimiterConfig(): RateLimiterConfig = RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofMillis(250))
            .timeoutDuration(Duration.ofMillis(25))
            .build()

    private fun circuitBreakerConfig(): CircuitBreakerConfig = CircuitBreakerConfig.custom()
        .failureRateThreshold(100.0f) // Open circuit on first failure
        .minimumNumberOfCalls(1)
        .slidingWindowSize(1)
        .waitDurationInOpenState(Duration.ofMillis(100))
        .build()

}