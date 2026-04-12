package com.assistant.e2e.runners

import net.serenitybdd.cucumber.CucumberWithSerenity
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(CucumberWithSerenity::class)
@CucumberOptions(
    features = ["src/test/resources/features/013-AIChatSidebar.feature"],
    glue = ["com.assistant.e2e.steps"],
    tags = "@ui",
    plugin = ["pretty"]
)
class UiAIChatSidebarRunner
