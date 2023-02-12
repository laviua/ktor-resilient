package ua.com.lavi.ktor.resilient.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import ua.com.lavi.ktor.resilient.client.plugins.*
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
    fun shouldRetry(): Unit = runBlocking {
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
            httpClient.get("http://127.0.0.1:9700/error")
        }
        retryModule.metrics.numberOfFailedCallsWithRetryAttempt shouldBe 2
    }

    @Test
    fun shouldNotRetry(): Unit = runBlocking {
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
        httpClient.get("http://127.0.0.1:9700/ok")
        retryModule.metrics.numberOfFailedCallsWithRetryAttempt shouldBe 0
    }


    @Test
    fun shouldSuccessNoTimeout(): Unit = runBlocking {

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
        val response = httpClient.get("http://127.0.0.1:9700/ok")
        val end = System.currentTimeMillis()
        println("Request took: ${end - start} ms")
        response.status.value shouldBe 200
    }

    @Test
    fun shouldFailBecauseOfTheTimeout(): Unit = runBlocking {

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
            httpClient.get("http://127.0.0.1:9700/delayed")
        }
    }

    @Test
    fun shouldRatelimit(): Unit = runBlocking {

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
            httpClient.get("http://127.0.0.1:9700/ok")
            httpClient.get("http://127.0.0.1:9700/ok")
        }
    }

    @Test
    fun shouldOkRatelimit(): Unit = runBlocking {
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
        httpClient.get("http://127.0.0.1:9700/ok")
        Thread.sleep(250)
        httpClient.get("http://127.0.0.1:9700/ok")
    }

    @Test
    fun shouldSuccessCircuitBreaker(): Unit = runBlocking {
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
        val response = httpClient.get("http://127.0.0.1:9700/ok")
        response.status.value shouldBe 200
        cb.state shouldBe CircuitBreaker.State.CLOSED
        cb.metrics.numberOfFailedCalls shouldBe 0
    }

    @Test
    fun shouldFailCircuitBreaker(): Unit = runBlocking {
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
            httpClient.get("http://127.0.0.1:9700/error")
        }
        cb.state shouldBe CircuitBreaker.State.OPEN
        cb.metrics.numberOfFailedCalls shouldBe 1

        shouldThrow<CallNotPermittedException> {
            httpClient.get("http://127.0.0.1:9700/ok")
        }
    }

    @Test
    fun shouldSuccessTestBulkhead() {

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
            async {
                httpClient.get("http://127.0.0.1:9700/ok1")
            }
            httpClient.get("http://127.0.0.1:9700/ok2")
        }
    }

    @Test
    fun shouldFailTestBulkhead() {

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
                async {
                    httpClient.get("http://127.0.0.1:9700/ok1")
                }
                httpClient.get("http://127.0.0.1:9700/ok2")
            }
        }
    }

    @Test
    fun complexPluginChain(): Unit = runBlocking {
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
            httpClient.get("http://127.0.0.1:9700/does not matter")
        }
        cb.state shouldBe CircuitBreaker.State.OPEN
        retryModule.metrics.numberOfFailedCallsWithRetryAttempt shouldBe 1
    }
}