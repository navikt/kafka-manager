package no.nav.kafkamanager.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class WebSecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.authorizeHttpRequests { auth ->
            auth
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers("/oauth2/**").permitAll()
                .anyRequest().authenticated()
        }
            .csrf { it.disable() }
            .oauth2Login { oAuth2LoginConfigurer ->
                oAuth2LoginConfigurer.redirectionEndpoint { it.baseUri("/oauth2/callback") }
                    .defaultSuccessUrl("/index.html")
            }
        return http.build()
    }
}