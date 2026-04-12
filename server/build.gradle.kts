plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm {
        mainRun {
            mainClass.set("com.assistant.server.ApplicationKt")
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))

            // Ktor Server
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.auth)
            implementation(libs.ktor.server.auth.jwt)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.ktor.server.cors)
            implementation(libs.ktor.server.call.logging)
            implementation(libs.ktor.serialization.kotlinx.json)

            // DI
            implementation(libs.koin.core)
            implementation(libs.koin.ktor)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Ktor Client Engine (required for HttpClient on JVM — used by AI agents, health checks)
            implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")

            // Logging
            implementation("ch.qos.logback:logback-classic:1.5.18")

            // SQLDelight JDBC driver (for KBRepository)
            implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(kotlin("test-junit5"))
            implementation(libs.ktor.server.test.host)
            implementation(libs.koin.test)

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

// Fat JAR task for Docker deployment
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("jira-assistant-server")
    archiveClassifier.set("all")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.assistant.server.ApplicationKt"
    }
    val jvmMain = kotlin.jvm().compilations["main"]
    from(jvmMain.output.allOutputs)
    dependsOn(jvmMain.compileTaskProvider)
    from({
        jvmMain.runtimeDependencyFiles.filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
}
