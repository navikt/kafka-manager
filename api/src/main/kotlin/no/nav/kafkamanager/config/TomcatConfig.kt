package no.nav.kafkamanager.config

import org.apache.tomcat.util.http.Rfc6265CookieProcessor
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class TomcatConfig {
    @Bean
    fun cookieProcessorCustomizer(): WebServerFactoryCustomizer<TomcatServletWebServerFactory> =
        WebServerFactoryCustomizer { tomcatServletWebServerFactory ->
            tomcatServletWebServerFactory.addContextCustomizers({ context ->
                val processor = Rfc6265CookieProcessor()
                processor.setSameSiteCookies("lax")
                context.cookieProcessor = processor
            })
        }
}
