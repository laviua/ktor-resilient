package ua.com.lavi.ktor.resilient.client.plugins

import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.Retry
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.util.*
import kotlinx.coroutines.CompletableJob

class RetryPlugin(private val config: RetryPluginConfiguration) {

    companion object Plugin : HttpClientPlugin<RetryPluginConfiguration, RetryPlugin>, HttpClientEngineCapability<RetryPluginConfiguration> {

        override val key: AttributeKey<RetryPlugin> = AttributeKey("Retry")

        override fun prepare(block: RetryPluginConfiguration.() -> Unit): RetryPlugin = RetryPlugin(RetryPluginConfiguration().apply(block))


        override fun install(plugin: RetryPlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val retry: Retry = plugin.config.retry
                val call = retry.executeSuspendFunction {
                    execute(request)
                }
                call
            }
        }

        private fun prepareRequest(request: HttpRequestBuilder): HttpRequestBuilder {
            val subRequest = HttpRequestBuilder().takeFrom(request)
            request.executionContext.invokeOnCompletion { cause ->
                val subRequestJob = subRequest.executionContext as CompletableJob
                if (cause == null) subRequestJob.complete()
                else subRequestJob.completeExceptionally(cause)
            }
            return subRequest
        }
    }
    class RetryPluginConfiguration {
        var retry: Retry = Retry.ofDefaults("default")
    }
}
fun HttpClientConfig<*>.retry(block: RetryPlugin.RetryPluginConfiguration.() -> Unit) {
    install(RetryPlugin) {
        block()
    }
}