package com.assistant.domain

import org.koin.dsl.module

/**
 * Shared Koin module for Domain layer logic.
 */
val domainModule = module {
    single { FeatureNetworkMapper(get()) }
    single { ScrumEstimator(get()) }
}
