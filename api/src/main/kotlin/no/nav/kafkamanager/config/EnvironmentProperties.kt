package no.nav.kafkamanager.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.env")
data class EnvironmentProperties (
    var appConfigJson: String = "",
    var onPremKafkaBrokersUrl: String = ""
)