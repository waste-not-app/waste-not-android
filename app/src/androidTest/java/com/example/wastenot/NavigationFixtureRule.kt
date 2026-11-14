package com.example.wastenot

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.dummy.DummyAccountSession
import com.example.wastenot.data.dummy.DummyActivityStore
import com.example.wastenot.data.dummy.DummyGuideStore
import com.example.wastenot.data.dummy.DummyScanRepository
import com.example.wastenot.data.local.ScanPhotoCache
import com.example.wastenot.data.repository.AccountSession
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.data.repository.GuideStore
import com.example.wastenot.data.repository.ScanRepository
import com.example.wastenot.model.User
import org.junit.rules.ExternalResource

/** Installs deterministic sources only for the lifetime of a navigation test. */
class NavigationFixtureRule : ExternalResource() {
    private val appearance = ThemeFixtureRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestWasteNotApplication
    override fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        app.fixtureContainer = object : AppContainer(context) {
            override val showOnboarding = false
            override val accountSession: AccountSession = DummyAccountSession(context.getSharedPreferences("dummy_session", Context.MODE_PRIVATE))
            override val guides: GuideStore = DummyGuideStore()
            override fun activityStore(user: User): ActivityStore = navigationActivityFixture(context, user)
            override fun scanRepository(cache: ScanPhotoCache): ScanRepository = DummyScanRepository()
        }
        try { appearance.reset() }
        catch (failure: Throwable) { app.fixtureContainer = null; throw failure }
    }
    override fun after() { try { appearance.restore() } finally { app.fixtureContainer = null } }
}

internal fun navigationActivityFixture(context: Context, user: User): ActivityStore = DummyActivityStore(
    context.getSharedPreferences("dummy_logs_${user.id}", Context.MODE_PRIVATE), user.timezone,
    seedInitialRecords = user.id == "dummy-owner",
)
