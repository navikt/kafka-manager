package no.nav.kafkamanager.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class WebSecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            authorizeHttpRequests {
                authorize("/internal/**", permitAll)
                authorize("/oauth2/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2Login {
                redirectionEndpoint { baseUri = "/oauth2/callback" }
                defaultSuccessUrl(
                    defaultSuccessUrl = "/index.html",
                    alwaysUse = false, // default-verdi i org.springframework.security.config.annotation.web.configurers#defaultSuccessUrl
                )
            }
        }
        return http.build()
    }
}
