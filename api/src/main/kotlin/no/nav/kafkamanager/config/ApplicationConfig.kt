package no.nav.kafkamanager.config

import no.nav.common.utils.Credentials
import no.nav.common.utils.NaisUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ApplicationConfig {

    @Bean
    fun serviceUserCredentials(): Credentials {
        return NaisUtils.getCredentials("service_user")
    }

}