package ua.com.lavi.ktor.resilient.examples

import io.ktor.client.call.*
import kotlinx.coroutines.runBlocking
import ua.com.lavi.ktor.resilient.ResilientCIOHttpClient

data class Ip(val ip: String)

fun main(args: Array<String>) {

    val client = ResilientCIOHttpClient()
    runBlocking {
        val ip: Ip = client.get("https://api.ipify.org?format=json").body()
        print("My ip: ${ip.ip}")
    }
}