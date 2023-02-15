package ua.com.lavi.ktor.resilient.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Test

class AsyncTest {

    data class DownloadedImage(val url: String,
                               val filename: String)

    @Test
    fun testCollector() {
        val asyncer = Asyncer()
        val collection = listOf("url1", "url2", "url3", "url4", "url5", "url6")
        val result: List<DownloadedImage> = asyncer.collector(collection, 2, ::downloader)
        result.size shouldBe 6
    }

    @Test
    fun testCollectorManyItems() {
        val asyncer = Asyncer()
        val collection = arrayListOf<String>()
        repeat(1000) {
            collection.add("url-$it")
        }
        val result: List<DownloadedImage> = asyncer.collector(collection, 100, ::downloader)
        result.size shouldBe 1000
    }

    @Test
    fun testSuspendCollector() {
        val asyncer = Asyncer()
        val collection = listOf("url1", "url2", "url3", "url4", "url5", "url6")
        val result: List<DownloadedImage> = asyncer.collectorSuspended(collection, 2, ::suspendDownloader)
        result.size shouldBe 6
    }

    @Test
    fun testMulticollector() {
        val asyncer = Asyncer()
        val collection = listOf("url1", "url2", "url3", "url4", "url5", "url6")
        val result: List<DownloadedImage> =
            asyncer.collector(collection, 2, ::multiDownloader).flatMap { it.map { it } }
        result.size shouldBe 6
    }

    @Test
    fun testCollectorWithException() {
        val asyncer = Asyncer()
        val collection = listOf("url1", "url2", "url3", "url4", "url5", "url6")
        shouldThrow<Exception> {
            asyncer.collector(collection, 2, ::downloaderWithException)
        }
    }

    @Test
    fun testProcessor() {
        val asyncer = Asyncer()
        val collection = listOf("url1", "url2", "url3", "url4", "url5", "url6")
        asyncer.processor(collection, 2, ::producer)
    }

    @Test
    fun testSuspendProcessor() {
        val asyncer = Asyncer()
        val collection = listOf("url1", "url2", "url3", "url4", "url5", "url6")
        asyncer.processorSuspended(collection, 2, ::suspendProducer)
    }


    fun downloader(url: String) : DownloadedImage {
        println("another message: $url")
        return DownloadedImage(url, "filename")
    }
    suspend fun suspendDownloader(url: String) : DownloadedImage {
        println("another message: $url")
        return DownloadedImage(url, "filename")
    }

    fun producer(url: String) {
        println(url)
    }

    suspend fun suspendProducer(url: String) {
        println(url)
    }

    fun downloaderWithException(url: String) : DownloadedImage {
        println(url)
        throw RuntimeException("Exception")
    }

    fun multiDownloader(url: String): List<DownloadedImage> {
        println("another message: $url")
        return arrayListOf(DownloadedImage(url, "filename"))
    }
}