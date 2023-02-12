package ua.com.lavi.ktor.resilient.client.plugins

import io.github.resilience4j.kotlin.timelimiter.executeSuspendFunction
import io.github.resilience4j.timelimiter.TimeLimiter
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.util.*

class TimeLimiterPlugin(private val config: TimeLimiterPluginConfiguration) {

    companion object Plugin : HttpClientPlugin<TimeLimiterPluginConfiguration, TimeLimiterPlugin> {

        override val key: AttributeKey<TimeLimiterPlugin> = AttributeKey("TimeLimiterPlugin")

        override fun prepare(block: TimeLimiterPluginConfiguration.() -> Unit): TimeLimiterPlugin =
            TimeLimiterPlugin(TimeLimiterPluginConfiguration().apply(block))

        override fun install(plugin: TimeLimiterPlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val timeLimiter: TimeLimiter = plugin.config.timeLimiter
                val call = timeLimiter.executeSuspendFunction {
                    execute(request)
                }
                call
            }
        }
    }

    class TimeLimiterPluginConfiguration {
        var timeLimiter: TimeLimiter = TimeLimiter.ofDefaults("default")
    }
}
fun HttpClientConfig<*>.timeLimiter(block: TimeLimiterPlugin.TimeLimiterPluginConfiguration.() -> Unit) {
    install(TimeLimiterPlugin) {
        block()
    }
}