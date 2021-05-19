package no.nav.kafkamanager.config

import no.nav.common.log.LogFilter
import no.nav.common.rest.filter.SetStandardHttpHeadersFilter
import no.nav.common.utils.EnvironmentUtils
import no.nav.kafkamanager.config.ApplicationConfig.Companion.DEFAULT_APPLICATION_NAME
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FilterConfig {

    @Bean
    fun logFilterRegistrationBean(): FilterRegistrationBean<LogFilter> {
        val registration = FilterRegistrationBean<LogFilter>()
        registration.filter = LogFilter(
            EnvironmentUtils.getApplicationName().orElse(DEFAULT_APPLICATION_NAME),
            EnvironmentUtils.isDevelopment().orElse(false)
        )
        registration.order = 1
        registration.addUrlPatterns("/api/*")
        return registration
    }

    @Bean
    fun setStandardHeadersFilterRegistrationBean(): FilterRegistrationBean<SetStandardHttpHeadersFilter> {
        val registration = FilterRegistrationBean<SetStandardHttpHeadersFilter>()
        registration.filter = SetStandardHttpHeadersFilter()
        registration.order = 2
        registration.addUrlPatterns("/api/*")
        return registration
    }

}