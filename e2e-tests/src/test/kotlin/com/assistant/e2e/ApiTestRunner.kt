package com.assistant.e2e

import net.serenitybdd.cucumber.CucumberWithSerenity
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(CucumberWithSerenity::class)
@CucumberOptions(
    features = ["src/test/resources/features"],
    glue = ["com.assistant.e2e.steps"],
    tags = "@api",
    plugin = ["pretty"]
)
class ApiTestRunner
