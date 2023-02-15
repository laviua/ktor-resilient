package ua.com.lavi.ktor.resilient.client

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.CopyOnWriteArrayList

open class Asyncer {

    // Helper function to handle the collection and apply the given function for each element in the collection
    fun <I,O> collector(collection: Collection<I>, capacity: Int, func: (input: I) -> O) = runBlocking {
        handleCollector(collection, capacity, func)
    }

    // Helper function to handle the collection and apply the given suspended function for each element in the collection
    fun <I,O> collectorSuspended(collection: Collection<I>, capacity: Int, func: suspend (input: I) -> O) = runBlocking {
        handleCollector(collection, capacity, func)
    }
    // Helper function to handle the collection and apply the given function for each element in the collection
    fun <I> processor(collection: Collection<I>, capacity: Int, func: (input: I) -> Unit): Unit = runBlocking {
        handleProcessor(collection, capacity, func)
    }
    // Helper function to handle the collection and apply the given suspended function for each element in the collection
    fun <I> processorSuspended(collection: Collection<I>, capacity: Int, func: suspend (input: I) -> Unit): Unit = runBlocking {
        handleProcessor(collection, capacity, func)
    }

    private fun <I,O> handleCollector(collection: Collection<I>, capacity: Int, func: suspend (I) -> O) = runBlocking {
        val result = CopyOnWriteArrayList<O>()
        var exception: Exception? = null
        val channel = Channel<I>(capacity = capacity)
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

    private fun <I> handleProcessor(collection: Collection<I>, capacity: Int, func: suspend (I) -> Unit) = runBlocking {
        var exception: Exception? = null
        val channel = Channel<I>(capacity = capacity)
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