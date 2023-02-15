package ua.com.lavi.ktor.resilient.jackson

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ObjectMapperFactoryTest {

    data class TestClass(val value: String,
                         val dateTime: OffsetDateTime
    )

    @Test
    fun shouldSerializeDateTimeIntoTheString() {
        val dateTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val serializedDateTime = ObjectMapperFactory.objectMapper.writeValueAsString(dateTime)
        serializedDateTime shouldBe "\"2023-01-01T00:00:00Z\""
    }

    @Test
    fun shouldSerAndDeSetDataClass() {
        val dateTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

        val sourceObject = TestClass(value = "testValue", dateTime = dateTime)
        val serializedObject = ObjectMapperFactory.objectMapper.writeValueAsString(sourceObject)
        val deserializedObject: TestClass = ObjectMapperFactory.objectMapper.readValue(serializedObject, TestClass::class.java)

        deserializedObject shouldBe sourceObject
    }
}