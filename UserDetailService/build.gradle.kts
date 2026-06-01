plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.8"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "1.9.25"
    jacoco
}

group = "it.polito.wa2"
version = "0.0.1-SNAPSHOT"
description = "UserDetailService"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-common:2.7.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")

    // security
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-security")


    // https://mvnrepository.com/artifact/org.keycloak/keycloak-admin-client
    implementation("org.keycloak:keycloak-admin-client:26.0.7")
    implementation("org.jboss.resteasy:resteasy-client:6.2.4.Final")

    // unit testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.12") // or latest version
    testImplementation("com.ninja-squad:springmockk:4.0.2") // or latest version
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")

    // test containers
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.github.dasniko:testcontainers-keycloak:3.6.0")
    testImplementation("io.jsonwebtoken:jjwt-api:0.12.3")
    testRuntimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    testRuntimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Observability - Loki log appender (LGTM stack)
    implementation("com.github.loki4j:loki-logback-appender:1.5.2")

    // Observability - Distributed Tracing (Micrometer + OTel bridge + OTLP exporter)
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    implementation("org.springframework.kafka:spring-kafka")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // report generato dopo i test
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // i test devono essere eseguiti prima del report
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.0".toBigDecimal() // soglia minima di copertura (0% per ora)
            }
        }
    }
}

jacoco {
    toolVersion = "0.8.11"
}