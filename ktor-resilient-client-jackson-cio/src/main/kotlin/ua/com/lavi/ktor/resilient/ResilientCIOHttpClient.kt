package ua.com.lavi.ktor.resilient

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import ua.com.lavi.ktor.resilient.client.ResilientHttpClient
import ua.com.lavi.ktor.resilient.jackson.ObjectMapperFactory

open class ResilientCIOHttpClient: ResilientHttpClient(
    client = HttpClient(CIO) {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(objectMapper = ObjectMapperFactory.objectMapper))
        }
    }
)