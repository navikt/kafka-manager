package no.nav.kafkamanager.config

import no.nav.kafkamanager.domain.AppConfig
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EnvironmentProperties::class)
class ApplicationConfig {
    @Bean
    fun appConfig(
        properties: EnvironmentProperties,
        objectMapper: ObjectMapper,
    ): AppConfig = objectMapper.readValue<AppConfig>(properties.appConfigJson)
}
