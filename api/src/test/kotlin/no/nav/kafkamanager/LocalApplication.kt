package no.nav.kafkamanager

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class LocalApplication

fun main(args: Array<String>) {
    val application = SpringApplication(LocalApplication::class.java)
    application.setAdditionalProfiles("local")
    application.run(*args)
}
