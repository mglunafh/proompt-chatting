plugins {
    alias(libs.plugins.ktlint) apply false
}

group = "dev.burufi.chatting.durable"
version = "0.1.0-SNAPSHOT"

subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
