plugins {
    alias(libs.plugins.ktlint) apply false
}

group = "dev.burufi.chatting.durable"
version = "0.1.0-SNAPSHOT"

subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    plugins.withId(rootProject.libs.plugins.kotlin.jvm.get().pluginId) {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
