package ua.com.lavi.ktor.resilient.client

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

@WireMockTest
class CIOTest {

    @Test
    fun shouldReturnOkResponse(wireMock: WireMockRuntimeInfo): Unit = runBlocking {
        val httpClient = HttpClient(CIO)
        stubFor(get("/testGet").willReturn(ok().withBody("""{"sampleBody" : "test"}""")))
        val body:String = httpClient.get("${wireMock.httpBaseUrl}/testGet").bodyAsText()
        body shouldBe """{"sampleBody" : "test"}"""
    }
}