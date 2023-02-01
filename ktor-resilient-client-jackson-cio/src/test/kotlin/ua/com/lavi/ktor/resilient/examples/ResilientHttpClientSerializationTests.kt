package ua.com.lavi.ktor.resilient.examples

import ua.com.lavi.ktor.resilient.ResilientCIOHttpClient
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import com.github.tomakehurst.wiremock.http.ContentTypeHeader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import ua.com.lavi.ktor.resilient.client.ResilientHttpClient

class ResilientHttpClientSerializationTests {

    data class SampleBody(val sampleBody: String)

    companion object {

        private val wireMockServer: WireMockServer = WireMockServer(
            WireMockConfiguration.options().port(9700).extensions(ResponseTemplateTransformer(true))
        )

        @JvmStatic
        @BeforeAll
        fun setup() {
            wireMockServer.start()

            wireMockServer.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/testGet"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json")
                            .withBody("""{"sampleBody" : "test"}""")
                    )
            )

            wireMockServer.stubFor(
                WireMock.post(WireMock.urlPathEqualTo("/testPost"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json")
                            .withBody("""{"sampleBody" : "test"}""")
                    )
            )
            wireMockServer.stubFor(
                WireMock.patch(WireMock.urlPathEqualTo("/testPatch"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json")
                            .withBody("""{"sampleBody" : "test"}""")
                    )
            )
            wireMockServer.stubFor(
                WireMock.put(WireMock.urlPathEqualTo("/testPut"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json")
                            .withBody("""{"sampleBody" : "test"}""")
                    )
            )

            wireMockServer.stubFor(
                WireMock.delete(WireMock.urlPathEqualTo("/testDelete"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json")
                            .withBody("""{"sampleBody" : "test"}""")
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
    fun shouldNotTransformBecauseOfAbsentSerializer(): Unit = runBlocking {
        val httpClient = ResilientHttpClient()
        shouldThrow<NoTransformationFoundException> {
            httpClient.get("http://127.0.0.1:9700/testGet").body<SampleBody>() shouldBe SampleBody("test")
        }
    }
    
    @Test
    fun shouldTransformWithJacksonSerializer(): Unit = runBlocking {
        val httpClient = ResilientCIOHttpClient()

        httpClient.get("http://127.0.0.1:9700/testGet").body<SampleBody>() shouldBe SampleBody("test")
        httpClient.post("http://127.0.0.1:9700/testPost", body = "ExpectedBody").body<SampleBody>() shouldBe SampleBody("test")
        httpClient.put("http://127.0.0.1:9700/testPut", body = "ExpectedBody").body<SampleBody>() shouldBe SampleBody("test")
        httpClient.patch("http://127.0.0.1:9700/testPatch", body = "ExpectedBody").body<SampleBody>() shouldBe SampleBody("test")
        httpClient.delete("http://127.0.0.1:9700/testDelete").body<SampleBody>() shouldBe SampleBody("test")
    }
}