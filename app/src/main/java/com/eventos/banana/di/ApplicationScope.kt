package com.eventos.banana.di

import javax.inject.Qualifier

/**
 * Qualifier for the application-level CoroutineScope.
 * Use this to inject a lifecycle-aware scope tied to the Application,
 * instead of creating rogue CoroutineScope(Dispatchers.IO) instances.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
