package dev.ntainy.guitar_tuner

import android.app.Application
import android.content.Context
import dev.ntainy.guitar_tuner.di.AppContainer

class TunerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as TunerApplication).container
