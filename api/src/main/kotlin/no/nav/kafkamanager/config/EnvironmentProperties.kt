package no.nav.kafkamanager.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.env")
class EnvironmentProperties(
    val appConfigJson: String = "",
    val onPremKafkaBrokersUrl: String = "",
    val onPremSchemaRegistryUrl: String = "",
    val serviceUserName: String = "",
    val serviceUserPassword: String = "",
)
