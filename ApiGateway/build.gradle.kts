plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.4.12"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "g11"
version = "0.0.1-SNAPSHOT"
description = "ApiGateway"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["springCloudVersion"] = "2024.0.2"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-mvc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Observability - Loki log appender (LGTM stack)
    implementation("com.github.loki4j:loki-logback-appender:1.5.2")
    // https://mvnrepository.com/artifact/org.apache.httpcomponents.client5/httpclient5
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5")
}

// OpenTelemetry Java Agent
val otelAgentVersion = "2.12.0"
val otelAgentFile = file("$buildDir/otel/opentelemetry-javaagent.jar")

tasks.register("downloadOtelAgent") {
    group = "observability"
    description = "Download the OpenTelemetry Java Agent JAR"
    outputs.file(otelAgentFile)
    doLast {
        otelAgentFile.parentFile.mkdirs()
        val url = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v$otelAgentVersion/opentelemetry-javaagent.jar"
        ant.invokeMethod("get", mapOf("src" to url, "dest" to otelAgentFile))
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    dependsOn("downloadOtelAgent")
    jvmArgs(
        "-javaagent:${otelAgentFile.absolutePath}",
    )
    environment("OTEL_SERVICE_NAME", "ApiGateway")
    environment("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318")
    environment("OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf")
    environment("OTEL_METRICS_EXPORTER", "otlp")
    environment("OTEL_TRACES_EXPORTER", "otlp")
    environment("OTEL_LOGS_EXPORTER", "none")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
