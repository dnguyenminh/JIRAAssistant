plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform {
                excludeTags("sequential")
            }
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":server:core"))

            // Ktor Server (routes need content-negotiation, auth, serialization)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.auth)
            implementation(libs.ktor.server.auth.jwt)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Ktor Client Engine (used by HttpMcpProtocolClient)
            implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")

            // DI
            implementation(libs.koin.core)
            implementation(libs.koin.ktor)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Logging
            implementation("ch.qos.logback:logback-classic:1.5.18")
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation(libs.ktor.server.test.host)
            implementation(libs.koin.test)
            implementation(project(":server:testing-support"))
            implementation(project(":server:chat"))  // Cross-module integration tests

            // JUnit 5
            implementation("org.junit.jupiter:junit-jupiter:${libs.versions.junit.get()}")
            runtimeOnly("org.junit.jupiter:junit-jupiter-engine:${libs.versions.junit.get()}")

            // Kotest property-based testing
            implementation("io.kotest:kotest-property:5.9.1")
            implementation("io.kotest:kotest-assertions-core:5.9.1")

            // Ktor Client MockEngine (for HttpClient unit tests)
            implementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
        }
    }
}
