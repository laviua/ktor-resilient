package ua.com.lavi.ktor.resilient.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

open class Asyncer {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun <I,O> collector(collection: Collection<I>, chunks: Int, func: (input: I) -> O) = runBlocking {
        handleCollector(collection, chunks, func)
    }

    fun <I,O> collector(collection: Collection<I>, chunks: Int, func: suspend (input: I) -> O) = runBlocking {
        handleCollector(collection, chunks, func)
    }

    fun <I> processor(collection: Collection<I>, chunks: Int, func: (input: I) -> Unit) = runBlocking {
        handleProcessor(collection, chunks, func)
    }
    fun <I> processor(collection: Collection<I>, chunks: Int, func: suspend (input: I) -> Unit) = runBlocking {
        handleProcessor(collection, chunks, func)
    }

    private fun <I,O> handleCollector(collection: Collection<I>, chunks: Int, func: suspend (I) -> O) = runBlocking {
        val result = CopyOnWriteArrayList<O>()
        var exception: Exception? = null
        for (chunk in collection.chunked(chunks)) {
            if (exception != null) break
            val deferreds = chunk.map { item ->
                async(Dispatchers.IO) {
                    try {
                        result.add(func(item))
                    } catch (e: Exception) {
                        log.warn("Error: ${e.message}", e)
                        exception = e
                    }
                    true
                }
            }
            log.debug("Waiting for all tasks in chunk: ${chunk.size}")
            deferreds.awaitAll()
            log.debug("Waited: ${chunk.size}")
        }
        if (exception != null) throw exception as Exception
        result
    }

    private fun <I> handleProcessor(collection: Collection<I>, chunks: Int, func: suspend (I) -> Unit) = runBlocking {
        var exception: Exception? = null
        for (chunk in collection.chunked(chunks)) {
            if (exception != null) break
            val deferreds = chunk.map { item ->
                async(Dispatchers.IO) {
                    try {
                        func(item)
                    } catch (e: Exception) {
                        log.warn("Error: ${e.message}", e)
                        exception = e
                    }
                    true
                }
            }
            log.debug("Waiting for all tasks in chunk: ${chunk.size}")
            deferreds.awaitAll()
            log.debug("Waited: ${chunk.size}")
        }
        if (exception != null) throw exception as Exception
    }

}