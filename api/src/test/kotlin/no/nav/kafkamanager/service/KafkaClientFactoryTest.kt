package no.nav.kafkamanager.service

import no.nav.common.utils.Credentials
import no.nav.kafkamanager.config.EnvironmentProperties
import no.nav.kafkamanager.domain.AppConfig
import no.nav.kafkamanager.domain.DeserializerType
import no.nav.kafkamanager.domain.TopicConfig
import no.nav.kafkamanager.domain.TopicLocation
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.env.StandardEnvironment
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Properties

/** Tester opprettelse av Kafka-klienter og valg av påloggingsinformasjon. */
class KafkaClientFactoryTest {
    /** Tester opprettelse av consumer-klienter for konfigurerte Kafka-topics. */
    @Nested
    inner class ConsumerCreation {
    @Test
    fun `Oppretter consumer for et tillatt on-prem Kafka-topic og bruker oppgitt gruppe-ID`() {
            // Arrange
            var receivedCredentials: Credentials? = null
            var receivedProperties: Properties? = null
            val propertiesFactory = object : KafkaPropertiesFactory(StandardEnvironment()) {
                override fun createOnPremConsumerProperties(
                    kafkaBrokerUrl: String,
                    credentials: Credentials,
                    schemaRegistryUrl: String?,
                    keyDeserializerType: DeserializerType,
                    valueDeserializerType: DeserializerType,
                    isolationLevel: String,
                ): Properties {
                    receivedCredentials = credentials
                    return consumerProperties().also { receivedProperties = it }
                }
            }
            val factory = KafkaClientFactory(
                EnvironmentProperties(serviceUserName = "synthetic-user", serviceUserPassword = "synthetic-password"),
                AppConfig(topics = listOf(topicConfig("allowed-topic", TopicLocation.ON_PREM))),
                propertiesFactory,
            )

            // Act
            val consumer = factory.createKafkaConsumerForTopic("test-group", "allowed-topic")

            // Assert
            assertNotNull(receivedCredentials)
            assertEquals("synthetic-user", receivedCredentials!!.username)
            assertEquals("test-group", receivedProperties!![ConsumerConfig.GROUP_ID_CONFIG])
            consumer.close()
        }

        @Test
        fun `Oppretter Aiven-consumer uten å hente on-prem-legitimasjon`() {
            // Arrange
            val properties = consumerProperties()
            val propertiesFactory = object : KafkaPropertiesFactory(StandardEnvironment()) {
                override fun createAivenConsumerProperties(
                    keyDeserializerType: DeserializerType,
                    valueDeserializerType: DeserializerType,
                    isolationLevel: String,
                ): Properties = properties
            }
            val factory = KafkaClientFactory(
                EnvironmentProperties(serviceUserName = "synthetic-user"),
                AppConfig(topics = listOf(topicConfig("aiven-topic", TopicLocation.AIVEN))),
                propertiesFactory,
            )

            // Act
            val consumer = factory.createKafkaConsumerForTopic("aiven-group", "aiven-topic")

            // Assert
            assertNotNull(consumer)
            consumer.close()
        }

        @Test
        fun `Ukjente Kafka-topics avvises før en klient opprettes`() {
            // Arrange
            val factory = createFactory(topics = emptyList())

            // Act
            val exception = assertThrows(ResponseStatusException::class.java) {
                factory.createKafkaConsumerForTopic(null, "unknown-topic")
            }

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        }
    }

    /** Tester opprettelse og tilgangskontroll for administrasjonsklienter. */
    @Nested
    inner class AdminClientCreation {
    @Test
    fun `Oppretter administrasjonsklient for et tillatt Kafka-topic med støtte for sletting`() {
            // Arrange
            val propertiesFactory = object : KafkaPropertiesFactory(StandardEnvironment()) {
                override fun createAivenAdminProperties(
                    keyDeserializerType: DeserializerType,
                    valueDeserializerType: DeserializerType,
                    isolationLevel: String,
                ): Properties = consumerProperties()
            }
            val factory = KafkaClientFactory(
                EnvironmentProperties(),
                AppConfig(topics = listOf(topicConfig("aiven-topic", TopicLocation.AIVEN))),
                propertiesFactory,
            )

            // Act
            val admin = factory.createKafkaAdminClientForTopic("aiven-topic")

            // Assert
            assertNotNull(admin)
            admin.close()
        }

        @Test
        fun `Avviser administrasjonsoperasjoner på Kafka-topics uten støtte`() {
            // Arrange
            val factory = createFactory(listOf(topicConfig("on-prem-topic", TopicLocation.ON_PREM)))

            // Act
            val exception = assertThrows(ResponseStatusException::class.java) {
                factory.createKafkaAdminClientForTopic("on-prem-topic")
            }

            // Assert
            assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.statusCode)
            assertEquals("Kafka admin operations are not supported for this topic", exception.reason)
        }

        @Test
        fun `Avviser Kafka-topics som ikke finnes i allowlisten`() {
            // Arrange
            val factory = createFactory(emptyList())

            // Act
            val exception = assertThrows(ResponseStatusException::class.java) {
                factory.createKafkaAdminClientForTopic("unknown-topic")
            }

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        }
    }

    /** Tester reglene for legitimasjon til on-prem consumers. */
    @Nested
    inner class OnPremCredentials {
    @Test
    fun `Bruker begge miljøverdiene når de er satt uten å kalle reserveoppslaget`() {
            // Arrange
            var fallbackCalled = false

            // Act
            val credentials = resolveServiceUserCredentials("synthetic-user", "synthetic-password") {
                fallbackCalled = true
                Credentials("mounted-user", "mounted-password")
            }

            // Assert
            assertEquals("synthetic-user", credentials.username)
            assertEquals("synthetic-password", credentials.password)
            assertFalse(fallbackCalled)
        }

        @Test
        fun `Bruker montert legitimasjon når begge miljøverdiene er blanke`() {
            // Arrange
            val mountedCredentials = Credentials("mounted-user", "mounted-password")
            var fallbackCalled = false

            // Act
            val credentials = resolveServiceUserCredentials("", "  ") {
                fallbackCalled = true
                mountedCredentials
            }

            // Assert
            assertSame(mountedCredentials, credentials)
            assertTrue(fallbackCalled)
        }

        @Test
        fun `Avviser brukernavn uten passord uten å røpe verdien eller bruke reserveoppslag`() {
            // Arrange
            val syntheticCanary = "synthetic-username-canary"
            var fallbackCalled = false

            // Act
            val exception = assertThrows(IllegalStateException::class.java) {
                resolveServiceUserCredentials(syntheticCanary, "") {
                    fallbackCalled = true
                    Credentials("mounted-user", "mounted-password")
                }
            }

            // Assert
            assertEquals(
                "SERVICE_USER_NAME and SERVICE_USER_PASSWORD must both be set or both be blank",
                exception.message,
            )
            assertFalse(exception.message.orEmpty().contains(syntheticCanary))
            assertFalse(fallbackCalled)
        }

        @Test
        fun `Avviser passord uten brukernavn uten å røpe verdien eller bruke reserveoppslag`() {
            // Arrange
            val syntheticCanary = "synthetic-password-canary"
            var fallbackCalled = false

            // Act
            val exception = assertThrows(IllegalStateException::class.java) {
                resolveServiceUserCredentials("", syntheticCanary) {
                    fallbackCalled = true
                    Credentials("mounted-user", "mounted-password")
                }
            }

            // Assert
            assertEquals(
                "SERVICE_USER_NAME and SERVICE_USER_PASSWORD must both be set or both be blank",
                exception.message,
            )
            assertFalse(exception.message.orEmpty().contains(syntheticCanary))
            assertFalse(fallbackCalled)
        }
    }

    /** Lager en klientfabrikk med de oppgitte Kafka-topic-konfigurasjonene. */
    private fun createFactory(topics: List<TopicConfig>) = KafkaClientFactory(
        EnvironmentProperties(),
        AppConfig(topics = topics),
        KafkaPropertiesFactory(StandardEnvironment()),
    )

    /** Lager konfigurasjon for et Kafka-topic med strengserialisering. */
    private fun topicConfig(
        name: String,
        location: TopicLocation,
    ) = TopicConfig(
        name = name,
        location = location,
        keyDeserializerType = DeserializerType.STRING,
        valueDeserializerType = DeserializerType.STRING,
        isolationLevel = null,
    )

    /** Lager minimale klientegenskaper for å opprette en Kafka-consumer i testen. */
    private fun consumerProperties() = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
    }
}
