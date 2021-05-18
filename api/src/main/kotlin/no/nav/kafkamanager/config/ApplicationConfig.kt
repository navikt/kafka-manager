package no.nav.kafkamanager.config

import no.nav.common.json.JsonUtils
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

    @Bean
    fun appConfig(properties: EnvironmentProperties): AppConfig {
        return JsonUtils.fromJson(properties.appConfigJson, AppConfig::class.java)
    }

    @Bean
    fun systemUserCredentialsSupplier(): Supplier<Credentials> {
        // Expects to find credentials mounted at /var/run/secrets/nais.io/service_user
        return Supplier { NaisUtils.getCredentials("service_user")  }
    }

}