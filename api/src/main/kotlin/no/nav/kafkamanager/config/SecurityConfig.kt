package no.nav.kafkamanager.config

import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusScrapeEndpoint
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher

/**
 * Konfigurerer tilgangskontroll og OAuth2-login.
 *
 * @param oauth2CallbackPath stien OAuth2-leverandøren bruker for callback.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig(
    @Value($$"${app.oauth2.callback-path}") private val oauth2CallbackPath: String,
) {
    /**
     * Oppretter sikkerhetsfilterkjeden for applikasjonens HTTP-endepunkter.
     *
     * @param http Spring Security-konfigurasjonen.
     * @return filterkjeden som håndhever tilgangsreglene.
     */
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { csrf -> csrf.spa() }
        http {
            // OAuth2-filtrene håndterer innlogging og callback før AuthorizationFilter.
            oauth2Login {
                redirectionEndpoint { baseUri = oauth2CallbackPath }
            }
            // Innloggingsrutene trenger derfor ikke egne permitAll-regler.
            authorizeHttpRequests {
                // Tillater interne ERROR-dispatcher, slik at tilgangskontrollen ikke erstatter feilresponsen.
                authorize(DispatcherTypeRequestMatcher(DispatcherType.ERROR), permitAll)
                authorize(
                    EndpointRequest.to(
                        HealthEndpoint::class.java,
                        PrometheusScrapeEndpoint::class.java,
                    ),
                    permitAll,
                )
                authorize(anyRequest, authenticated)
            }
        }
        return http.build()
    }
}
