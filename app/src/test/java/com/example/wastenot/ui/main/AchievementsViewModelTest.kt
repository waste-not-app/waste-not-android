package com.example.wastenot.ui.main

import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.model.AchievementRequirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var network: WasteNotNetwork
    private lateinit var viewModel: AchievementsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        server = MockWebServer().apply { start() }
        network = requireNotNull(
            WasteNotNetwork.create(
                baseUrl = server.url("/v1/").toString(),
                accessToken = { "token" },
                allowLocalHttp = true,
            ),
        )
        viewModel = AchievementsViewModel(CoreRepository(network))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        network.client.dispatcher.executorService.shutdown()
        network.client.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun loadErrorAndRefreshPreserveExpectedState() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"code":"UNAVAILABLE","message":"down"}}"""))
        viewModel.load()
        val failedState = withTimeout(5_000) { viewModel.state.first { !it.loading && it.error != null } }
        assertNull(failedState.items)
        assertFalse(failedState.loading)
        assertEquals(ApiFailureKind.SERVER, failedState.error)

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"data":[{
                  "id":"00000000-0000-4000-8000-000000000101",
                  "code":"FIRST_STEP",
                  "title":"Langkah pertama",
                  "description":"Catat minimal 1 aktivitas.",
                  "requirement_type":"ACTIVE_LOG_COUNT",
                  "current_value":1,
                  "target_value":1,
                  "is_unlocked":true,
                  "unlocked_at":"2026-11-13T07:00:00Z"
                }]}
                """.trimIndent(),
            ),
        )
        viewModel.refresh()
        val refreshed = withTimeout(5_000) { viewModel.state.first { !it.loading && it.items != null } }
        val items = refreshed.items!!
        assertEquals(1, items.size)
        assertEquals(AchievementRequirement.ACTIVE_LOG_COUNT, items[0].requirementType)
        assertNull(refreshed.error)
    }
}
