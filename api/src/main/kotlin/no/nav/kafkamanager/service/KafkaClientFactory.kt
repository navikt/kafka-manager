package no.nav.kafkamanager.service

import no.nav.common.utils.Credentials
import no.nav.common.utils.NaisUtils
import no.nav.kafkamanager.config.EnvironmentProperties
import no.nav.kafkamanager.domain.AppConfig
import no.nav.kafkamanager.domain.TopicConfig
import no.nav.kafkamanager.domain.TopicLocation
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig.DEFAULT_ISOLATION_LEVEL
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.Properties

/**
 * Oppretter Kafka-klienter med konfigurasjon tilpasset det angitte Kafka-topicet.
 *
 * @param environmentProperties miljøavhengige adresser og tjenestebrukeropplysninger.
 * @param appConfig konfigurasjonen av Kafka-topicene som klientene kan brukes mot.
 * @param kafkaPropertiesFactory lager Kafka-egenskaper for klientene.
 */
@Component
class KafkaClientFactory(
    private val environmentProperties: EnvironmentProperties,
    private val appConfig: AppConfig,
    private val kafkaPropertiesFactory: KafkaPropertiesFactory,
) {
    /**
     * Oppretter en Kafka-consumer med konfigurasjonen for Kafka-topicet.
     *
     * @param consumerGroupId consumer-gruppen som skal brukes, eller `null` dersom ingen gruppe-ID skal angis.
     * @param topicName navnet på det konfigurerte Kafka-topicet.
     * @return en Kafka-consumer for Kafka-topicet.
     * @throws ResponseStatusException hvis Kafka-topicet ikke finnes i konfigurasjonen.
     */
    fun createKafkaConsumerForTopic(
        consumerGroupId: String?,
        topicName: String,
    ): KafkaConsumer<Any?, Any?> {
        val topicConfig = findTopicConfigOrThrow(topicName)
        val properties = createConsumerPropertiesForTopic(consumerGroupId, topicConfig)

        return KafkaConsumer(properties)
    }

    /**
     * Oppretter en administrasjonsklient med konfigurasjonen for Kafka-topicet.
     *
     * @param topicName navnet på det konfigurerte Kafka-topicet.
     * @return en Kafka-administrasjonsklient for Kafka-topicet.
     * @throws ResponseStatusException hvis Kafka-topicet ikke finnes eller ikke ligger i Aiven.
     */
    fun createKafkaAdminClientForTopic(topicName: String): AdminClient {
        validateKafkaAdminTopic(topicName)
        return createAivenAdminClient()
    }

    /**
     * Oppretter en administrasjonsklient for Aiven-clusteret uten topic-spesifikk konfigurasjon.
     * Kalleren må lukke klienten etter bruk.
     *
     * @return en Kafka-administrasjonsklient med broker-adresse og autentisering fra Aiven-miljøet.
     */
    fun createAivenAdminClient(): AdminClient = AdminClient.create(kafkaPropertiesFactory.createAivenAdminProperties())

    /**
     * Validerer at topicet er konfigurert og ligger i Aiven, uten å opprette en Kafka-klient.
     *
     * @param topicName navnet på Kafka-topicet som skal valideres.
     * @return konfigurasjonen for det validerte topicet.
     * @throws ResponseStatusException med status 400 hvis topicet ikke er konfigurert,
     * eller 501 hvis det ikke ligger i Aiven.
     */
    fun validateKafkaAdminTopic(topicName: String): TopicConfig {
        val topicConfig = findTopicConfigOrThrow(topicName)
        if (topicConfig.location != TopicLocation.AIVEN) {
            throw ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Kafka admin operations are not supported for this topic",
            )
        }
        return topicConfig
    }

    /**
     * Lager consumer-egenskaper ut fra Kafka-topicets plassering og konfigurerte serialiseringstyper.
     *
     * @param consumerGroupId consumer-gruppen som skal angis, eller `null` dersom gruppe-ID skal utelates.
     * @param topicConfig konfigurasjonen for Kafka-topicet.
     * @return egenskapene som brukes når Kafka-consumeren opprettes.
     */
    private fun createConsumerPropertiesForTopic(
        consumerGroupId: String?,
        topicConfig: TopicConfig,
    ): Properties {
        val keyDesType = topicConfig.keyDeserializerType
        val valueDesType = topicConfig.valueDeserializerType
        val isolationLevel = topicConfig.isolationLevel ?: DEFAULT_ISOLATION_LEVEL

        val properties = when (topicConfig.location) {
            TopicLocation.ON_PREM -> kafkaPropertiesFactory.createOnPremConsumerProperties(
                kafkaBrokerUrl = environmentProperties.onPremKafkaBrokersUrl,
                credentials = systemUserCredentials(),
                schemaRegistryUrl = environmentProperties.onPremSchemaRegistryUrl,
                keyDeserializerType = keyDesType,
                valueDeserializerType = valueDesType,
                isolationLevel = isolationLevel,
            )

            TopicLocation.AIVEN -> kafkaPropertiesFactory.createAivenConsumerProperties(
                keyDeserializerType = keyDesType,
                valueDeserializerType = valueDesType,
                isolationLevel = isolationLevel,
            )
        }

        if (consumerGroupId != null) {
            properties[ConsumerConfig.GROUP_ID_CONFIG] = consumerGroupId
        }

        return properties
    }

    /**
     * Henter tjenestebrukerens påloggingsopplysninger fra konfigurasjonen eller NAIS.
     *
     * @return tjenestebrukerens påloggingsopplysninger.
     * @throws IllegalStateException hvis bare brukernavn eller passord er konfigurert.
     */
    private fun systemUserCredentials(): Credentials = resolveServiceUserCredentials(
        serviceUserName = environmentProperties.serviceUserName,
        serviceUserPassword = environmentProperties.serviceUserPassword,
    ) {
        NaisUtils.getCredentials("service_user")
    }

    /**
     * Finner konfigurasjonen for Kafka-topicet med det angitte navnet.
     *
     * @param topicName navnet på Kafka-topicet som skal finnes.
     * @return konfigurasjonen for Kafka-topicet.
     * @throws ResponseStatusException hvis Kafka-topicet ikke finnes i konfigurasjonen.
     */
    private fun findTopicConfigOrThrow(topicName: String): TopicConfig = appConfig.topics.find { it.name == topicName }
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find config for topic")
}

/**
 * Velger tjenestebrukeropplysninger fra konfigurasjonen eller bruker reservefunksjonen.
 *
 * @param serviceUserName konfigurert brukernavn.
 * @param serviceUserPassword konfigurert passord.
 * @param fallback funksjon som henter opplysninger dersom brukernavn og passord begge er blanke.
 * @return tjenestebrukerens påloggingsopplysninger.
 * @throws IllegalStateException hvis bare brukernavn eller passord er blankt.
 */
internal fun resolveServiceUserCredentials(
    serviceUserName: String,
    serviceUserPassword: String,
    fallback: () -> Credentials,
): Credentials {
    val hasUserName = serviceUserName.isNotBlank()
    val hasPassword = serviceUserPassword.isNotBlank()

    if (hasUserName != hasPassword) {
        throw IllegalStateException("SERVICE_USER_NAME and SERVICE_USER_PASSWORD must both be set or both be blank")
    }

    return if (hasUserName) {
        Credentials(serviceUserName, serviceUserPassword)
    } else {
        fallback()
    }
}
