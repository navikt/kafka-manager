import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
}

val javaVersion = 25
val commonVersion = "4.2026.10.19_05.55-7add0b457c1d"

group = "no.nav.kafkamanager"
version = "4.1.2"

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion.toString())
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencyManagement {
    dependencies {
        dependency("org.apache.commons:commons-compress:1.28.0")
        dependency("org.xerial.snappy:snappy-java:1.1.10.12")
        dependency("at.yawk.lz4:lz4-java:1.13.0")
        dependency("com.google.guava:guava:33.8.0-jre")
        dependency("ch.qos.logback:logback-classic:1.6.6")
        dependency("ch.qos.logback:logback-core:1.6.6")
        dependency("org.json:json:20260814")
    }
}

dependencies {
    // Spring Boot and Kotlin
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation(kotlin("reflect"))

    // Kafka and Avro
    implementation("org.apache.avro:avro:1.12.2")
    implementation("io.confluent:kafka-avro-serializer:8.4.0")

    // Nav Common
    implementation("no.nav.common:log:$commonVersion")
    implementation("no.nav.common:kafka:$commonVersion")
    implementation("no.nav.common:util:$commonVersion")
    implementation("no.nav.common:rest:$commonVersion")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("com.ninja-squad:springmockk:5.0.1")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs("-Xshare:off", "-XX:+EnableDynamicAgentLoading")
}

// Spring Boot-apper kjøres via bootJar, ikke den vanlige jar-tasken.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<BootJar>("bootJar") {
    dependsOn(":web-app:assembleFrontend")
    from(project(":web-app").layout.projectDirectory.dir("dist")) {
        into("BOOT-INF/classes/static")
    }
    archiveFileName.set("kafka-manager.jar")
}
