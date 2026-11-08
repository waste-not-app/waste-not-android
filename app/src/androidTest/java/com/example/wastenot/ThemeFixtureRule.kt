package com.example.wastenot

import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

class ThemeFixtureRule(private val enabled: () -> Boolean = { true }) : ExternalResource() {
    private var original: ThemeMode? = null
    private val store get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WasteNotApplication).container.appearance
    internal fun reset() {
        if (!enabled()) return
        runBlocking { original = store.modes.first(); store.write(ThemeMode.SYSTEM) }
    }
    internal fun restore() {
        original?.let { mode -> runBlocking { store.write(mode) } }
        original = null
    }
    override fun before() = reset()
    override fun after() = restore()
}
