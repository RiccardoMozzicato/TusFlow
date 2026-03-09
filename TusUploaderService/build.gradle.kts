plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.springframework.boot") version "3.5.8"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.springdoc.openapi-gradle-plugin") version "1.8.0"
    jacoco
}

group = "G11"
version = "0.0.1-SNAPSHOT"
description = "TusUploaderService"

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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    //LAB 2 - JPA Dependency - Docker - MinIO
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("io.minio:minio-admin:8.5.7")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.apache.tika:tika-core:2.9.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.12") // or latest version
    testImplementation("com.ninja-squad:springmockk:4.0.2") // or latest version
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0") // Per supporto Mockito in Kotlin
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testcontainers for integration tests
    testImplementation("org.testcontainers:testcontainers:1.20.3")
    testImplementation("org.testcontainers:junit-jupiter:1.20.3")
    testImplementation("org.testcontainers:postgresql:1.20.3")
    testImplementation("org.testcontainers:kafka:1.20.3")
    testImplementation("com.github.dasniko:testcontainers-keycloak:3.0.0") // Keycloak Testcontainer per OAuth2 testing

    //flyway per migrazioni DB
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Database in-memory per i test
    testImplementation("com.h2database:h2")

    //Lab 4 - Security
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client") //this is useless here, we are already carrying a token
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server") //should use this
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-mvc:4.3.2")

    //Lab  5 - Kafka
    implementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.security:spring-security-test") // Spring Security Test per @WithMockUser
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
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

openApi {
    // URL predefinito per il tuo server locale in esecuzione durante la fase di generazione
    apiDocsUrl.set("http://localhost:8080/v3/api-docs.yaml")

    // Directory di output nella root del progetto (persiste anche dopo gradle clean)
    outputDir.set(file("$projectDir/docs/openapi"))

    // Nome del file OpenAPI generato
    outputFileName.set("openapi.yaml")

}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-reflect:1.9.25")
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.25")
    }
}