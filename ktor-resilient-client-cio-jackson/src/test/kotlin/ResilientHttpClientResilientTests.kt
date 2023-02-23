
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ua.com.lavi.ktor.resilient.client.ResilientClient
import ua.com.lavi.ktor.resilient.jackson.ObjectMapperFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException


@WireMockTest
class ResilientHttpClientResilientTests {

    @BeforeEach
    fun setup(wireMock: WireMockRuntimeInfo) {
        stubFor(get("/empty").willReturn(ok().withBody("ok")))
        stubFor(get("/delayed").willReturn(ok().withBody("ok").withFixedDelay(200)))
        stubFor(get("/error").willReturn(serverError().withBody("server error")))
    }

    private val engine = HttpClient(CIO) {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(objectMapper = ObjectMapperFactory.objectMapper))
        }
    }

    @Test
    fun shouldFailBecauseOfTimeout(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val httpClient = ResilientClient(httpClient = engine, timeLimiter = TimeLimiter.of(Duration.of(10, ChronoUnit.MILLIS)))
        shouldThrow<TimeoutException> {
            httpClient.get("${wireMock.httpBaseUrl}/delayed")
        }
    }

    @Test
    fun shouldNotFailBecauseOfTimeout(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val httpClient = ResilientClient(httpClient = engine, timeLimiter = TimeLimiter.of(Duration.of(1000, ChronoUnit.MILLIS)))
        httpClient.get("${wireMock.httpBaseUrl}/delayed").status shouldBe HttpStatusCode.OK
    }

    @Test
    fun shouldRetryBecauseOfTheError(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val config = RetryConfig.custom<HttpResponse>()
            .maxAttempts(5)
            .retryOnResult { response: HttpResponse -> response.status.value == 500 }
            .retryExceptions(Exception::class.java)
            .waitDuration(Duration.ofMillis(1))
            .build()

        val httpClient = ResilientClient(httpClient = engine, retry = Retry.of("test", config))
        val response = httpClient.get("${wireMock.httpBaseUrl}/error")
        response.status shouldBe HttpStatusCode.InternalServerError

        wireMock.wireMock.verifyThat(5, getRequestedFor(urlEqualTo("/error")))
    }

    @Test
    fun shouldIgnoreTimeLimiterBecauseOfCustomResilient(wireMock: WireMockRuntimeInfo): Unit = runBlocking {

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

        httpClient.get("${wireMock.httpBaseUrl}/delayed").status shouldBe HttpStatusCode.OK
    }

    @Test
    fun noResilienceTest(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
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

        httpClient.get("${wireMock.httpBaseUrl}/empty").status shouldBe HttpStatusCode.OK
    }
}