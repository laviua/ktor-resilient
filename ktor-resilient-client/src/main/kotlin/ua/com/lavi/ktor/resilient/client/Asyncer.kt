package ua.com.lavi.ktor.resilient.client

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.CopyOnWriteArrayList

open class Asyncer {

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
        val channel = Channel<I>(capacity = chunks)
        val job = launch {
            for (item in channel) {
                try {
                    result.add(func(item))
                } catch (e: Exception) {
                    exception = e
                    break
                }
            }
        }
        for (item in collection) {
            if (exception != null) break
            channel.send(item)
        }
        channel.close()
        job.join()
        if (exception != null) throw exception as Exception
        result
    }


    private fun <I> handleProcessor(collection: Collection<I>, chunks: Int, func: suspend (I) -> Unit) = runBlocking {
        var exception: Exception? = null
        val channel = Channel<I>(capacity = chunks)
        val job = launch {
            for (item in channel) {
                try {
                  func(item)
                } catch (e: Exception) {
                    exception = e
                    break
                }
            }
        }
        for (item in collection) {
            if (exception != null) break
            channel.send(item)
        }
        channel.close()
        job.join()
        if (exception != null) throw exception as Exception
    }

}