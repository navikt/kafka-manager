package no.nav.kafkamanager.config

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.common.utils.Credentials
import no.nav.common.utils.NaisUtils
import no.nav.kafkamanager.domain.AppConfig
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Supplier

@Configuration
@EnableConfigurationProperties(EnvironmentProperties::class)
class ApplicationConfig {

    companion object {
        const val DEFAULT_APPLICATION_NAME = "kafka-manager"
    }

    @Bean
    fun appConfig(properties: EnvironmentProperties, objectMapper: ObjectMapper): AppConfig {
        return objectMapper.readValue(properties.appConfigJson, AppConfig::class.java)
    }

    @Bean
    fun systemUserCredentialsSupplier(): Supplier<Credentials> {
        // Expects to find credentials mounted at /var/run/secrets/nais.io/service_user
        return Supplier { NaisUtils.getCredentials("service_user")  }
    }

}