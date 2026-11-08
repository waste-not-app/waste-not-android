package com.example.wastenot

import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.local.ScanPhotoCache
import com.example.wastenot.data.repository.RemoteAccountSession
import com.example.wastenot.data.repository.RemoteActivityStore
import com.example.wastenot.data.repository.RemoteGuideStore
import com.example.wastenot.data.repository.RemoteScanRepository
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.User
import java.io.File
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class LiveDataConfigurationTest {
    @Test fun applicationDefaultsToRemoteSourcesWithoutAnAutomaticAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = AppContainer(context)
        assertTrue(BuildConfig.API_BASE_URL.startsWith("https://"))
        assertTrue(app.accountSession is RemoteAccountSession)
        assertTrue(app.accountSession.state.value is SessionState.Checking)
        assertTrue(app.guides is RemoteGuideStore)
        val user = User("configuration-check", "Configuration check", "configuration@example.test", ZoneId.of("Asia/Jakarta"), null)
        assertTrue(app.activityStore(user) is RemoteActivityStore)
        val directory = File(context.cacheDir, "configuration-check-${System.nanoTime()}")
        try { assertTrue(app.scanRepository(ScanPhotoCache(directory, "configuration-check") {}) is RemoteScanRepository) }
        finally { directory.deleteRecursively() }
    }
}
