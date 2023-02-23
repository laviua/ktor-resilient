import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.ContentTypeHeader
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ua.com.lavi.ktor.resilient.client.ResilientClient
import ua.com.lavi.ktor.resilient.jackson.ObjectMapperFactory

@WireMockTest
class ResilientHttpClientSerializationTests {


    @BeforeEach
    fun setup(wireMock: WireMockRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/testGet")).willReturn(WireMock.aResponse().withStatus(200).withHeader(ContentTypeHeader.KEY, "application/json").withBody("""{"sampleBody" : "test"}""")))
        stubFor(post(urlPathEqualTo("/testPost")).willReturn(WireMock.aResponse().withStatus(200).withHeader(ContentTypeHeader.KEY, "application/json").withBody("""{"sampleBody" : "test"}""")))
        stubFor(patch(urlPathEqualTo("/testPatch")).willReturn(WireMock.aResponse().withStatus(200).withHeader(ContentTypeHeader.KEY, "application/json").withBody("""{"sampleBody" : "test"}""")))
        stubFor(put(urlPathEqualTo("/testPut")).willReturn(WireMock.aResponse().withStatus(200).withHeader(ContentTypeHeader.KEY, "application/json").withBody("""{"sampleBody" : "test"}""")))
        stubFor(delete(urlPathEqualTo("/testDelete")).willReturn(WireMock.aResponse().withStatus(200).withHeader(ContentTypeHeader.KEY, "application/json").withBody("""{"sampleBody" : "test"}""")))
    }

    data class SampleBody(val sampleBody: String)


    @Test
    fun shouldNotTransformBecauseOfAbsentSerializer(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val httpClient = ResilientClient(httpClient = HttpClient(CIO))
        shouldThrow<NoTransformationFoundException> {
            httpClient.get("${wireMock.httpBaseUrl}/testGet").body<SampleBody>() shouldBe SampleBody("test")
        }
    }

    @Test
    fun shouldTransformWithJacksonSerializer(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val httpClient = ResilientClient(httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(objectMapper = ObjectMapperFactory.objectMapper))
            }
        })

        httpClient.get("${wireMock.httpBaseUrl}/testGet").body<SampleBody>() shouldBe SampleBody("test")
        httpClient.post("${wireMock.httpBaseUrl}/testPost", body = "ExpectedBody").body<SampleBody>() shouldBe SampleBody("test")
        httpClient.put("${wireMock.httpBaseUrl}/testPut", body = "ExpectedBody").body<SampleBody>() shouldBe SampleBody("test")
        httpClient.patch("${wireMock.httpBaseUrl}/testPatch", body = "ExpectedBody").body<SampleBody>() shouldBe SampleBody("test")
        httpClient.delete("${wireMock.httpBaseUrl}/testDelete").body<SampleBody>() shouldBe SampleBody("test")
    }
}