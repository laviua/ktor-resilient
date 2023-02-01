package ua.com.lavi.ktor.resilient.examples

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import com.github.tomakehurst.wiremock.http.ContentTypeHeader
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import ua.com.lavi.ktor.resilient.client.ResilientHttpClient

class ResilientHttpClientHttpMethodTests {

    companion object {

        val ACCESS_TOKEN = "X-Secret-Token"

        private val wireMockServer: WireMockServer = WireMockServer(
            WireMockConfiguration.options().port(9700).extensions(ResponseTemplateTransformer(true))
        )

        @JvmStatic
        @BeforeAll
        fun setup() {
            wireMockServer.start()

            wireMockServer.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/testGet"))
                    .withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json")
                            .withBody("{\"GET\"}")
                    )
            )
            wireMockServer.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/testGetNoHeadersRequired"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json")
                            .withBody("{\"GET\"}")
                    )
            )

            wireMockServer.stubFor(
                WireMock.post(WireMock.urlPathEqualTo("/testPost"))
                    .withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN))
                    .withRequestBody(EqualToPattern("ExpectedBody"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json")
                            .withBody("{\"POST\"}")
                    )
            )
            wireMockServer.stubFor(
                WireMock.patch(WireMock.urlPathEqualTo("/testPatch"))
                    .withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN))
                    .withRequestBody(EqualToPattern("ExpectedBody"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json")
                            .withBody("{\"PATCH\"}")
                    )
            )
            wireMockServer.stubFor(
                WireMock.put(WireMock.urlPathEqualTo("/testPut"))
                    .withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN))
                    .withRequestBody(EqualToPattern("ExpectedBody"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json")
                            .withBody("{\"PUT\"}")
                    )
            )
            wireMockServer.stubFor(
                WireMock.head(WireMock.urlPathEqualTo("/testHead"))
                    .withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                    )
            )
            wireMockServer.stubFor(
                WireMock.options(WireMock.urlPathEqualTo("/testOptions"))
                    .withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                    )
            )
            wireMockServer.stubFor(
                WireMock.delete(WireMock.urlPathEqualTo("/testDelete"))
                    .withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json")
                            .withBody("{\"DELETE\"}")
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
    fun shouldDoCorrectHttpMethods() = runBlocking {
        val httpClient = ResilientHttpClient()
        
        httpClient.get("http://127.0.0.1:9700/testGet", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.get("http://127.0.0.1:9700/testGetNoHeadersRequired").status shouldBe HttpStatusCode.OK
        httpClient.post("http://127.0.0.1:9700/testPost", body = "ExpectedBody", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.put("http://127.0.0.1:9700/testPut", body = "ExpectedBody", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.patch("http://127.0.0.1:9700/testPatch", body = "ExpectedBody", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.head("http://127.0.0.1:9700/testHead", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.options("http://127.0.0.1:9700/testOptions", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.delete("http://127.0.0.1:9700/testDelete", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK

    }
}