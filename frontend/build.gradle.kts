plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "frontend.js"
                cssSupport { enabled.set(true) }
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":shared"))

            // Ktor Client (JS engine)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation("io.ktor:ktor-client-js:${libs.versions.ktor.get()}")

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // Coroutines JS
            implementation(libs.kotlinx.coroutines.core)

            // kotlinx-html for DSL-based DOM building
            implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.11.0")

            // Cytoscape.js (Knowledge Graph canvas renderer)
            implementation(npm("cytoscape", "^3.33.2"))
        }

        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
