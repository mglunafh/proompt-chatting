plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

dependencies {
    implementation(platform(libs.bom.ktor))
    implementation(platform(libs.bom.kotlinx.coroutines))
    implementation(platform(libs.bom.exposed))

    implementation(project(":step2-durable:shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.password4j)
    implementation(libs.caffeine)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.logstash.logback.encoder)

    testImplementation(testFixtures(project(":step2-durable:shared")))
    testImplementation(platform(libs.bom.junit))
    testImplementation(platform(libs.bom.testcontainers))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("dev.burufi.chatting.durable.server.ServerKt")
}

tasks.shadowJar {
    archiveFileName.set("server.jar")
    // Flyway's database modules, the JDBC driver and Ktor all resolve through META-INF/services.
    // Without the merge the jar builds and then fails at boot, which is the worse failure.
    // INCLUDE is what lets the merge see every copy: under the default EXCLUDE all but the
    // first are dropped before the transformer runs, which silently cost flyway-core 36 of its
    // 39 plugin entries.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

// Gradle's -D reaches the Gradle JVM, not the application's so we got to pass
// application-related properties through to make an effect.
tasks.named<JavaExec>("run") {
    listOf("server.", "db.").forEach { prefix ->
        systemProperties(providers.systemPropertiesPrefixedBy(prefix).get())
    }
}
