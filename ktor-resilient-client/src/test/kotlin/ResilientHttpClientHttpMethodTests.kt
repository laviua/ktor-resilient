package ua.com.lavi.ktor.resilient.client

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.http.ContentTypeHeader
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@WireMockTest
class ResilientHttpClientHttpMethodTests {

    private val ACCESS_TOKEN = "X-Secret-Token"

    @BeforeEach
    fun setup(wireMock: WireMockRuntimeInfo) {
        stubFor(WireMock.get(WireMock.urlPathEqualTo("/testGet")).withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN)).willReturn(WireMock.aResponse().withStatus(200).withHeader(ContentTypeHeader.KEY, "application/json").withBody("{\"GET\"}")))
        stubFor(WireMock.get(WireMock.urlPathEqualTo("/testGetNoHeadersRequired")).willReturn(WireMock.aResponse().withStatus(200).withHeader(ContentTypeHeader.KEY, "application/json").withBody("{\"GET\"}")))
        stubFor(WireMock.post(WireMock.urlPathEqualTo("/testPost")).withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN)).withRequestBody(EqualToPattern("ExpectedBody")).willReturn(WireMock.aResponse().withStatus(200).withHeader(ContentTypeHeader.KEY, "application/json").withBody("{\"POST\"}")))
        stubFor(WireMock.patch(WireMock.urlPathEqualTo("/testPatch")).withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN)).withRequestBody(EqualToPattern("ExpectedBody")).willReturn(WireMock.aResponse().withStatus(200).withHeader(ContentTypeHeader.KEY, "application/json").withBody("{\"PATCH\"}")))
        stubFor(WireMock.put(WireMock.urlPathEqualTo("/testPut")).withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN)).withRequestBody(EqualToPattern("ExpectedBody")).willReturn(WireMock.aResponse().withStatus(200).withHeader(ContentTypeHeader.KEY, "application/json").withBody("{\"PUT\"}")))
        stubFor(WireMock.head(WireMock.urlPathEqualTo("/testHead")).withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN)).willReturn(WireMock.aResponse().withStatus(200)))
        stubFor(WireMock.options(WireMock.urlPathEqualTo("/testOptions")).withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN)).willReturn(WireMock.aResponse().withStatus(200)))
        stubFor(WireMock.delete(WireMock.urlPathEqualTo("/testDelete")).withHeader(HttpHeaders.Authorization, EqualToPattern(ACCESS_TOKEN)).willReturn(WireMock.aResponse().withStatus(200).withHeader(ContentTypeHeader.KEY, "application/json").withBody("{\"DELETE\"}")))
    }


    @Test
    fun shouldDoCorrectHttpMethods(wireMock: WireMockRuntimeInfo) = runBlocking {
        val httpClient = ResilientClient(httpClient = HttpClient(CIO))

        httpClient.get("${wireMock.httpBaseUrl}/testGet", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.get("${wireMock.httpBaseUrl}/testGetNoHeadersRequired").status shouldBe HttpStatusCode.OK
        httpClient.post("${wireMock.httpBaseUrl}/testPost", body = "ExpectedBody", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.put("${wireMock.httpBaseUrl}/testPut", body = "ExpectedBody", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.patch("${wireMock.httpBaseUrl}/testPatch", body = "ExpectedBody", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.head("${wireMock.httpBaseUrl}/testHead", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.options("${wireMock.httpBaseUrl}/testOptions", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK
        httpClient.delete("${wireMock.httpBaseUrl}/testDelete", headers = mapOf("Authorization" to ACCESS_TOKEN)).status shouldBe HttpStatusCode.OK

    }
}