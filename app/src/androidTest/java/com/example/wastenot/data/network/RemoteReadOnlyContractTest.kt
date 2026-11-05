package com.example.wastenot.data.network

import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RemoteReadOnlyContractTest {
    @Test fun configuredHttpsApiReturnsTheAnonymousMetadataChallenge() = runTest {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyLiveReadOnly") == "true")
        assertFalse(BuildConfig.USE_DUMMY_DATA)
        assertTrue(BuildConfig.API_BASE_URL.startsWith("https://"))
        val network = requireNotNull(WasteNotNetwork.create(BuildConfig.API_BASE_URL))
        try {
            val response = network.api.metadata()
            val result = network.executor.execute({ it.toModel() }) { response }
            assertTrue("Anonymous metadata must be rejected by the configured server: $result", result is ApiResult.Failure)
            val failure = (result as ApiResult.Failure).error
            assertEquals(ApiFailureKind.UNAUTHENTICATED, failure.kind)
            assertEquals(401, failure.status)
            assertEquals("UNAUTHENTICATED", failure.code)
            assertNotNull(failure.requestId)
        } finally {
            network.client.dispatcher.cancelAll()
            network.client.connectionPool.evictAll()
            network.client.dispatcher.executorService.shutdown()
        }
    }
}
