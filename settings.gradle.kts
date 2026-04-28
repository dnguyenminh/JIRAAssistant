rootProject.name = "JiraAssistant"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/stable")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/stable")
    }
}

include(":shared")
include(":server")
include(":server:core")
include(":server:dashboard")
include(":server:analysis")
include(":server:docgen")
include(":server:agent")
include(":server:chat")
include(":server:mcp")
include(":server:knowledge-graph")
include(":server:user-mgmt")
include(":server:testing-support")
include(":frontend")
include(":e2e-tests")
