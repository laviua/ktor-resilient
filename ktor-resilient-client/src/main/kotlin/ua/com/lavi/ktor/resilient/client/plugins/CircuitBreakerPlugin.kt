package ua.com.lavi.ktor.resilient.client.plugins

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.util.*

class CircuitBreakerPlugin(private val config: CircuitBreakerPluginConfiguration) {

    companion object Plugin : HttpClientPlugin<CircuitBreakerPluginConfiguration, CircuitBreakerPlugin> {

        override val key: AttributeKey<CircuitBreakerPlugin> = AttributeKey("CircuitBreaker")

        override fun prepare(block: CircuitBreakerPluginConfiguration.() -> Unit): CircuitBreakerPlugin =
            CircuitBreakerPlugin(CircuitBreakerPluginConfiguration().apply(block))

        override fun install(plugin: CircuitBreakerPlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val circuitBreaker: CircuitBreaker = plugin.config.circuitBreaker
                val call = circuitBreaker.executeSuspendFunction {
                    execute(request)
                }
                call
            }
        }
    }

    class CircuitBreakerPluginConfiguration {
        var circuitBreaker: CircuitBreaker = CircuitBreaker.ofDefaults("default")
    }
}
fun HttpClientConfig<*>.circuitBreaker(block: CircuitBreakerPlugin.CircuitBreakerPluginConfiguration.() -> Unit) {
    install(CircuitBreakerPlugin) {
        block()
    }
}