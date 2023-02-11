package ua.com.lavi.ktor.resilient.client.plugins

import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.util.*

class RateLimiterPlugin(private val config: RateLimiterPluginConfiguration) {

    companion object Plugin : HttpClientPlugin<RateLimiterPluginConfiguration, RateLimiterPlugin> {

        override val key: AttributeKey<RateLimiterPlugin> = AttributeKey("RateLimiter")

        override fun prepare(block: RateLimiterPluginConfiguration.() -> Unit): RateLimiterPlugin =
            RateLimiterPlugin(RateLimiterPluginConfiguration().apply(block))

        override fun install(plugin: RateLimiterPlugin, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                val rateLimiter: RateLimiter = plugin.config.rateLimiter
                rateLimiter.executeSuspendFunction { proceed() }
            }
        }
    }

    class RateLimiterPluginConfiguration {
        var rateLimiter: RateLimiter = RateLimiter.ofDefaults("default")
    }
}
fun HttpClientConfig<*>.rateLimiter(block: RateLimiterPlugin.RateLimiterPluginConfiguration.() -> Unit) {
    install(RateLimiterPlugin) {
        block()
    }
}