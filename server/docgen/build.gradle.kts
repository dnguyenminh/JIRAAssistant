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
            workingDir = rootProject.projectDir
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":server:core"))
            implementation(project(":server:analysis"))
            implementation(project(":server:agent"))

            // Ktor Server (routes need content-negotiation, auth, serialization)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.auth)
            implementation(libs.ktor.server.auth.jwt)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Ktor Client Engine (used by document generation pipelines)
            implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")

            // DI
            implementation(libs.koin.core)
            implementation(libs.koin.ktor)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Logging
            implementation("ch.qos.logback:logback-classic:1.5.18")

            // PostgreSQL JDBC driver (for PgCollectionJobRepository)
            implementation("org.postgresql:postgresql:42.7.5")

            // pgvector Java support
            implementation("com.pgvector:pgvector:0.1.6")
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation(libs.ktor.server.test.host)
            implementation(libs.koin.test)
            implementation(project(":server:testing-support"))

            // JUnit 5
            implementation("org.junit.jupiter:junit-jupiter:${libs.versions.junit.get()}")
            runtimeOnly("org.junit.jupiter:junit-jupiter-engine:${libs.versions.junit.get()}")

            // Kotest property-based testing
            implementation("io.kotest:kotest-property:5.9.1")
            implementation("io.kotest:kotest-assertions-core:5.9.1")

            // Ktor Client MockEngine (for HttpClient unit tests)
            implementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")

            // SQLDelight JDBC driver (for in-memory test DB)
            implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
        }
    }
}
