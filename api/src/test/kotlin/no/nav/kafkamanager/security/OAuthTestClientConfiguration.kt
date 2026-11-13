package no.nav.kafkamanager.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType

/** Konfigurerer en lokal OAuth2-klient for tester uten ekstern identitetsleverandør. */
@TestConfiguration(proxyBeanMethods = false)
class OAuthTestClientConfiguration {
    /**
     * Oppretter klientregistreringen som Spring Security bruker til OAuth2-login i testene.
     *
     * @param callbackPath callback-stien fra app-konfigurasjonen.
     */
    @Bean
    fun clientRegistrationRepository(
        @Value($$"${app.oauth2.callback-path}") callbackPath: String,
    ): ClientRegistrationRepository {
        val registration = ClientRegistration
            .withRegistrationId("azure")
            .clientId("test-client")
            .clientSecret("test-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}$callbackPath")
            .scope("openid")
            .authorizationUri("https://idp.invalid/authorize")
            .tokenUri("https://idp.invalid/token")
            .userInfoUri("https://idp.invalid/userinfo")
            .userNameAttributeName("sub")
            .clientName("Kafka Manager")
            .build()
        return InMemoryClientRegistrationRepository(registration)
    }
}
