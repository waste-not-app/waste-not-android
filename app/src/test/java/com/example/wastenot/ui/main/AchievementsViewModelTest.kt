package com.example.wastenot.ui.main

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.AchievementStore
import com.example.wastenot.model.Achievement
import java.time.Instant
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.model.AchievementRequirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runCurrent
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var network: WasteNotNetwork
    private lateinit var viewModel: AchievementsViewModel
    private val acknowledgements = MutableSharedFlow<String>(replay = 1)
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }

    private fun createModel(store: AchievementStore = CoreRepository(network)) = ViewModelProvider.create(owner, viewModelFactory { initializer {
        AchievementsViewModel(store, acknowledgements)
    } })[AchievementsViewModel::class]

    private fun achievementResponse(current: Int) = MockResponse().setResponseCode(200).setBody(
        """{"data":[{"id":"00000000-0000-4000-8000-000000000101","code":"FIRST_STEP","title":"Langkah pertama","description":"Catat minimal 1 aktivitas.","requirement_type":"ACTIVE_LOG_COUNT","current_value":$current,"target_value":1,"is_unlocked":${current > 0},"unlocked_at":${if (current > 0) "\"2026-11-13T07:00:00Z\"" else "null"}}]}""",
    )

    private suspend fun awaitState(predicate: (AchievementsUiState) -> Boolean): AchievementsUiState =
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { viewModel.state.first(predicate) }
        }

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
        viewModel = createModel()
    }

    @After
    fun tearDown() {
        owner.viewModelStore.clear()
        Dispatchers.resetMain()
        network.client.dispatcher.executorService.shutdown()
        network.client.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun loadErrorAndRefreshPreserveExpectedState() = runTest(mainDispatcher) {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"code":"UNAVAILABLE","message":"down"}}"""))
        viewModel.load()
        val failedState = awaitState { !it.loading && it.error != null }
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
        val refreshed = awaitState { !it.loading && it.items != null }
        val items = refreshed.items!!
        assertEquals(1, items.size)
        assertEquals(AchievementRequirement.ACTIVE_LOG_COUNT, items[0].requirementType)
        assertNull(refreshed.error)
    }

    @Test
    fun delayedWorkerAcknowledgementRefreshesLoadedAchievementsButDuplicateRevisionsDoNotReload() = runTest(mainDispatcher) {
        server.enqueue(achievementResponse(0))
        viewModel.load()
        awaitState { !it.loading && it.items != null }
        assertEquals(0L, viewModel.state.value.items!!.single().currentValue)
        assertEquals(1, server.requestCount)

        // No local mutation callback refreshes achievements. Only the later worker acknowledgement does.
        server.enqueue(achievementResponse(1))
        acknowledgements.emit("acknowledged-create")
        awaitState { !it.loading && it.items?.single()?.currentValue == 1L }
        assertEquals(2, server.requestCount)
        acknowledgements.emit("acknowledged-create")
        runCurrent()
        assertFalse(viewModel.state.value.loading)
        assertEquals(2, server.requestCount)

        owner.viewModelStore.clear()
        viewModel = createModel()
        assertNull(viewModel.state.value.items)
        assertEquals(2, server.requestCount)
        server.enqueue(achievementResponse(1))
        viewModel.load()
        awaitState { !it.loading && it.items != null }
        assertEquals(1L, viewModel.state.value.items!!.single().currentValue)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun acknowledgementDuringAnOlderRequestCannotBeOverwrittenByItsDelayedResponse() = runTest(mainDispatcher) {
        val firstResponse = CompletableDeferred<Achievement>()
        var firstFinished = false
        var calls = 0
        val latest = Achievement(
            id = "00000000-0000-4000-8000-000000000101",
            code = "FIRST_STEP", title = "Langkah pertama", description = "Catat minimal 1 aktivitas.",
            requirementType = AchievementRequirement.ACTIVE_LOG_COUNT,
            currentValue = 1, targetValue = 1, isUnlocked = true,
            unlockedAt = Instant.parse("2026-11-13T07:00:00Z"),
        )
        val store = object : AchievementStore {
            override suspend fun achievements(): ApiResult<List<Achievement>> {
                calls++
                return if (calls == 1) withContext(NonCancellable) {
                    val response = firstResponse.await()
                    firstFinished = true
                    ApiResult.Success(listOf(response))
                } else ApiResult.Success(listOf(latest))
            }
        }
        owner.viewModelStore.clear()
        viewModel = createModel(store)
        try {
            viewModel.load()
            runCurrent()
            assertEquals(1, calls)
            acknowledgements.emit("acknowledged-update")
            runCurrent()
            assertEquals(1L, viewModel.state.value.items!!.single().currentValue)
            assertFalse(viewModel.state.value.loading)
            firstResponse.complete(latest.copy(currentValue = 0, isUnlocked = false, unlockedAt = null))
            runCurrent()
            assertEquals(true, firstFinished)
            assertEquals(2, calls)
            assertEquals(1L, viewModel.state.value.items!!.single().currentValue)
            assertFalse(viewModel.state.value.loading)
            assertNull(viewModel.state.value.error)
        } finally {
            firstResponse.cancel()
            runCurrent()
        }
    }
}
