package no.nav.kafkamanager.config

import no.nav.common.rest.filter.LogRequestFilter
import no.nav.common.rest.filter.SetStandardHttpHeadersFilter
import no.nav.common.utils.EnvironmentUtils
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class FilterConfig {
    @Bean
    fun logFilterRegistrationBean() = FilterRegistrationBean<LogRequestFilter>().apply {
        @Suppress("TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        setFilter(
            LogRequestFilter(
                EnvironmentUtils.getApplicationName().orElse(DEFAULT_APPLICATION_NAME),
                EnvironmentUtils.isDevelopment().orElse(false),
            ),
        )
        order = 1
        addUrlPatterns("/api/*")
    }

    @Bean
    fun setStandardHeadersFilterRegistrationBean() = FilterRegistrationBean<SetStandardHttpHeadersFilter>().apply {
        setFilter(SetStandardHttpHeadersFilter())
        order = 2
        addUrlPatterns("/api/*")
    }

    companion object {
        const val DEFAULT_APPLICATION_NAME = "kafka-manager"
    }
}
