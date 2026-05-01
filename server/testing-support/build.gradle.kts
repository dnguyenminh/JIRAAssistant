plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":server:core"))

            // Test libraries exposed as API so dependents get them transitively
            implementation(kotlin("test-junit5"))
            implementation(libs.ktor.server.test.host)
            implementation(libs.koin.test)

            // JUnit 5
            implementation("org.junit.jupiter:junit-jupiter:${libs.versions.junit.get()}")

            // Kotest property-based testing
            implementation("io.kotest:kotest-property:5.9.1")
            implementation("io.kotest:kotest-assertions-core:5.9.1")

            // Coroutines (for test utilities that use suspend functions)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
