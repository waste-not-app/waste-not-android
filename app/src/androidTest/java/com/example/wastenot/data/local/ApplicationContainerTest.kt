package com.example.wastenot.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.SessionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationContainerTest {
    @Test
    fun applicationSharesOneContainerAndBlocksUnverifiedPrivateRequests() = runTest {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WasteNotApplication
        val first = app.container
        assertSame(first, app.container)
        assertSame(first.sessions, app.container.sessions)
        assertEquals(SessionState.Checking, first.sessions.state.value)
        assertEquals(ApiFailureKind.UNAUTHENTICATED, (first.core.metadata() as ApiResult.Failure).error.kind)
    }
}
