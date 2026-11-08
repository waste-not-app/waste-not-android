package com.example.wastenot

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class WasteNotTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, TestWasteNotApplication::class.java.name, context)
}

class TestWasteNotApplication : WasteNotApplication() {
    var fixtureContainer: AppContainer? = null
    override val container: AppContainer get() = fixtureContainer ?: super.container
}
