package no.nav.kafkamanager

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class Application {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun logApplicationVersion(
        @Value($$"${app.version:unknown}") appVersion: String,
    ) = ApplicationRunner {
        logger.info(
            "Starting kafka-manager version={}",
            appVersion,
        )
    }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
