package ua.com.lavi.ktor.resilient.client.plugins

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.kotlin.bulkhead.executeSuspendFunction
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.util.*

class BulkheadPlugin(private val config: BulkheadPluginConfiguration) {

    companion object Plugin : HttpClientPlugin<BulkheadPluginConfiguration, BulkheadPlugin> {

        override val key: AttributeKey<BulkheadPlugin> = AttributeKey("BulkheadPlugin")

        override fun prepare(block: BulkheadPluginConfiguration.() -> Unit): BulkheadPlugin =
            BulkheadPlugin(BulkheadPluginConfiguration().apply(block))

        override fun install(plugin: BulkheadPlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val bulkhead: Bulkhead = plugin.config.bulkhead
                val call = bulkhead.executeSuspendFunction {
                    execute(request)
                }
                call
            }
        }
    }

    class BulkheadPluginConfiguration {
        var bulkhead: Bulkhead =  Bulkhead.ofDefaults("default")
    }
}
fun HttpClientConfig<*>.bulkhead(block: BulkheadPlugin.BulkheadPluginConfiguration.() -> Unit) {
    install(BulkheadPlugin) {
        block()
    }
}