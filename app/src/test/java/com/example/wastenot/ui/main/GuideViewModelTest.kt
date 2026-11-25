package com.example.wastenot.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.data.dummy.DummyGuideStore
import com.example.wastenot.data.network.*
import com.example.wastenot.data.repository.GuideStore
import com.example.wastenot.model.*
import java.net.URI
import java.time.LocalDate
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owners = mutableListOf<ViewModelStoreOwner>()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { owners.forEach { it.viewModelStore.clear() }; Dispatchers.resetMain() }
    private fun model(store: GuideStore, saved: SavedStateHandle = SavedStateHandle(),
        now: () -> Instant = Instant::now): GuideViewModel {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        owners.add(owner)
        return ViewModelProvider.create(owner, viewModelFactory { initializer { GuideViewModel(store, saved, now) } })[GuideViewModel::class]
    }

    @Test fun dummySearchTrimsMatchesKeywordsAndCombinesCategory() = runTest {
        val source = DummyGuideStore()
        val found = source.search(" CARTON ") as ApiResult.Success
        assertEquals(listOf(WasteCategory.PAPER), found.value.map { it.category })
        assertEquals(emptyList<WasteGuide>(), (source.search("carton", WasteCategory.GLASS) as ApiResult.Success).value)
        assertFalse(found.page!!.hasMore)
        assertEquals(ApiFailureKind.VALIDATION, (source.search("x".repeat(101)) as ApiResult.Failure).error.kind)
        assertEquals(ApiFailureKind.NOT_FOUND, (source.get("removed") as ApiResult.Failure).error.kind)
        assertTrue(DummyGuideStore.fixtures.all { it.review == null })
    }

    @Test fun cursorPagesDoNotRepeatItemsAndInvalidCursorsAreRejected() = runTest {
        val source = DummyGuideStore((0..40).map { DummyGuideStore.fixtures.first().copy(id = "guide-$it") })
        val first = source.search("") as ApiResult.Success
        val second = source.search("", cursor = first.page!!.nextCursor) as ApiResult.Success
        val last = source.search("", cursor = second.page!!.nextCursor) as ApiResult.Success
        assertEquals(listOf(20, 20, 1), listOf(first.value.size, second.value.size, last.value.size))
        assertEquals(41, (first.value + second.value + last.value).map { it.id }.toSet().size)
        assertNull(last.page!!.nextCursor)
        listOf("-1", "abc", "42", "2147483647").forEach { cursor ->
            assertEquals(ApiFailureKind.INVALID_REQUEST, (source.search("", cursor = cursor) as ApiResult.Failure).error.kind)
        }
    }

    @Test fun debounceSendsOnlyTheNewestTrimmedQueryAndLimitsInput() = runTest {
        val calls = mutableListOf<String>()
        val source = DummyGuideStore()
        val vm = model(object : GuideStore by source {
            override suspend fun search(query: String, category: WasteCategory?, cursor: String?): ApiResult<List<WasteGuide>> {
                calls.add(query); return source.search(query, category, cursor)
            }
        })
        vm.load(); advanceUntilIdle(); calls.clear()
        vm.query("ca"); advanceTimeBy(200); vm.query(" CARTON "); advanceTimeBy(299); runCurrent()
        assertTrue(calls.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf("CARTON"), calls)
        assertEquals("Paper and cardboard", vm.state.value.articles.single().name)
        vm.query("x".repeat(101)); advanceUntilIdle()
        assertEquals(100, vm.state.value.query.length)
        assertEquals(100, calls.last().length)
    }

    @Test fun explicitSubmitSkipsDebounceAndKeepsPriorRowsUntilTheNewSearchSucceeds() = runTest {
        val source = DummyGuideStore()
        val replacement = CompletableDeferred<ApiResult<List<WasteGuide>>>()
        val calls = mutableListOf<String>()
        val vm = model(object : GuideStore by source {
            override suspend fun search(query: String, category: WasteCategory?, cursor: String?): ApiResult<List<WasteGuide>> {
                calls += query
                return if (query == "carton") replacement.await() else source.search(query, category, cursor)
            }
        })
        vm.load(); runCurrent()
        val initial = vm.state.value.articles
        vm.query("car"); runCurrent()
        vm.query("carton"); runCurrent()
        assertEquals(listOf(""), calls)
        assertEquals(initial, vm.state.value.articles)
        assertTrue(vm.state.value.showingPreviousResults)
        assertFalse(vm.state.value.loading)

        vm.search(); runCurrent()
        assertEquals(listOf("", "carton"), calls)
        assertEquals(initial, vm.state.value.articles)
        assertTrue(vm.state.value.loading)
        assertTrue(vm.state.value.showingPreviousResults)
        vm.search()
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf("", "carton"), calls)

        replacement.complete(source.search("carton")); runCurrent()
        assertEquals("Paper and cardboard", vm.state.value.articles.single().name)
        assertFalse(vm.state.value.showingPreviousResults)
    }

    @Test fun changingCategoryRejectsAnOldPageAndCannotReuseItsCursor() = runTest {
        val catalog = (0..20).map { DummyGuideStore.fixtures.first().copy(id = "guide-$it") }
        val source = DummyGuideStore(catalog + DummyGuideStore.fixtures.filter { it.category == WasteCategory.GLASS })
        val previousPage = CompletableDeferred<ApiResult<List<WasteGuide>>>()
        val cursors = mutableListOf<String?>()
        val vm = model(object : GuideStore by source {
            override suspend fun search(query: String, category: WasteCategory?, cursor: String?): ApiResult<List<WasteGuide>> {
                cursors += cursor
                return if (cursor == null) source.search(query, category, cursor)
                else withContext(NonCancellable) { previousPage.await() }
            }
        })
        vm.load(); runCurrent()
        vm.search(more = true); runCurrent()
        vm.category(WasteCategory.GLASS)
        assertTrue(vm.state.value.showingPreviousResults)
        assertEquals(20, vm.state.value.articles.size)
        vm.search(more = true); runCurrent()
        previousPage.complete(source.search("", cursor = "20")); advanceUntilIdle()

        assertEquals(listOf(null, "20", null), cursors)
        assertEquals("Glass containers", vm.state.value.articles.single().name)
        assertFalse(vm.state.value.showingPreviousResults)
        assertFalse(vm.state.value.loadingMore)
    }

    @Test fun failedNewSearchKeepsThePreviousRowsMarkedAndDoesNotPageThem() = runTest {
        val source = DummyGuideStore((0..20).map { DummyGuideStore.fixtures.first().copy(id = "guide-$it") })
        val calls = mutableListOf<Pair<String, String?>>()
        val vm = model(object : GuideStore by source {
            override suspend fun search(query: String, category: WasteCategory?, cursor: String?): ApiResult<List<WasteGuide>> {
                calls += query to cursor
                return if (query.isNotEmpty()) ApiResult.Failure(ApiFailure(ApiFailureKind.NETWORK))
                else source.search(query, category, cursor)
            }
        })
        vm.load(); runCurrent()
        val initial = vm.state.value.articles
        vm.query("carton"); vm.search(); runCurrent()
        vm.search(more = true); runCurrent()

        assertEquals(listOf("" to null, "carton" to null), calls)
        assertEquals(initial, vm.state.value.articles)
        assertEquals(ApiFailureKind.NETWORK, vm.state.value.error)
        assertTrue(vm.state.value.showingPreviousResults)
        assertNull(vm.state.value.nextCursor)
    }

    @Test fun unicodeQueryLimitMatchesServerCodePointsAndSurvivesRestoration() = runTest {
        val source = DummyGuideStore()
        val symbol = String(Character.toChars(0x1F331))
        val expected = symbol.repeat(100)
        val saved = SavedStateHandle(mapOf("guide_query" to symbol.repeat(101)))
        val vm = model(source, saved)
        assertEquals(expected, vm.state.value.query)
        vm.load(); advanceUntilIdle()
        assertNull(vm.state.value.error)
        vm.query("x"); vm.query(symbol.repeat(101)); advanceUntilIdle()
        assertEquals(expected, saved.get<String>("guide_query"))
        assertNull(vm.state.value.error)
        assertEquals(ApiFailureKind.VALIDATION, (source.search(symbol.repeat(101)) as ApiResult.Failure).error.kind)
    }

    @Test fun retryAfterBlocksNewQueriesDetailsAndRetryUntilDeadlineWithoutCrossingOwners() = runTest {
        val entries = (0..40).map { DummyGuideStore.fixtures.first().copy(id = "guide-$it") }
        val source = DummyGuideStore(entries)
        var time = Instant.parse("2026-11-06T00:00:00Z")
        val deadline = time.plusSeconds(60)
        var reads = 0; var details = 0
        val store = object : GuideStore by source {
            override suspend fun search(query: String, category: WasteCategory?, cursor: String?): ApiResult<List<WasteGuide>> {
                reads++
                return if (reads == 2) ApiResult.Failure(ApiFailure(ApiFailureKind.RATE_LIMITED, retryAt = deadline))
                    else source.search(query, category, cursor)
            }
            override suspend fun get(id: String): ApiResult<WasteGuide> { details++; return source.get(id) }
        }
        val vm = model(store, now = { time })
        vm.load(); advanceUntilIdle()
        assertNotNull(vm.state.value.nextCursor)
        vm.search(more = true); advanceUntilIdle()
        assertEquals(20, vm.state.value.articles.size)
        vm.query("carton"); vm.search(); vm.detail(entries.first().id); advanceUntilIdle()
        assertEquals(2, reads)
        assertNull(vm.state.value.nextCursor)
        assertEquals(0, details)
        assertEquals(ApiFailureKind.RATE_LIMITED, vm.state.value.error)
        assertEquals(ApiFailureKind.RATE_LIMITED, vm.state.value.detail!!.error)
        val otherOwner = model(store, now = { time })
        otherOwner.load(); advanceUntilIdle()
        assertEquals(3, reads)
        assertNull(otherOwner.state.value.error)
        vm.search(); advanceUntilIdle(); assertEquals(3, reads)
        time = deadline
        vm.search(); vm.detail(entries.first().id, retry = true); advanceUntilIdle()
        assertEquals(4, reads)
        assertEquals(1, details)
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.detail!!.article)
    }

    @Test fun cancelledResponseCannotReplaceTheLatestSearchEvenIfStoreIgnoresCancellation() = runTest {
        val old = CompletableDeferred<ApiResult<List<WasteGuide>>>()
        val source = DummyGuideStore()
        val vm = model(object : GuideStore by source {
            override suspend fun search(query: String, category: WasteCategory?, cursor: String?) =
                if (query == "bottle") withContext(NonCancellable) { old.await() } else source.search(query, category, cursor)
        })
        vm.query("bottle"); advanceTimeBy(300); runCurrent()
        vm.query("carton"); advanceTimeBy(300); runCurrent()
        assertEquals("Paper and cardboard", vm.state.value.articles.single().name)
        old.complete(source.search("bottle")); advanceUntilIdle()
        assertEquals("carton", vm.state.value.query)
        assertEquals("Paper and cardboard", vm.state.value.articles.single().name)
    }

    @Test fun laterPageFailureKeepsTheFirstPageAndExplicitRetryUsesItsCursor() = runTest {
        var fail = true
        val source = DummyGuideStore((0..20).map { DummyGuideStore.fixtures.first().copy(id = "guide-$it") })
        val vm = model(object : GuideStore by source {
            override suspend fun search(query: String, category: WasteCategory?, cursor: String?): ApiResult<List<WasteGuide>> =
                if (cursor != null && fail) ApiResult.Failure(ApiFailure(ApiFailureKind.NETWORK)) else source.search(query, category, cursor)
        })
        vm.load(); advanceUntilIdle(); vm.search(more = true); advanceUntilIdle()
        assertEquals(20, vm.state.value.articles.size)
        assertEquals("20", vm.state.value.nextCursor)
        assertEquals(ApiFailureKind.NETWORK, vm.state.value.error)
        fail = false; vm.search(more = true); advanceUntilIdle()
        assertEquals(21, vm.state.value.articles.size)
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.nextCursor)
    }

    @Test fun refreshingTheSameSearchKeepsVisibleArticlesWhenTheRequestFails() = runTest {
        var fail = false
        val source = DummyGuideStore()
        val vm = model(object : GuideStore by source {
            override suspend fun search(query: String, category: WasteCategory?, cursor: String?): ApiResult<List<WasteGuide>> =
                if (fail) ApiResult.Failure(ApiFailure(ApiFailureKind.NETWORK)) else source.search(query, category, cursor)
        })
        vm.load(); advanceUntilIdle()
        val articles = vm.state.value.articles
        fail = true; vm.search()
        assertEquals(articles, vm.state.value.articles)
        assertTrue(vm.state.value.loading)
        advanceUntilIdle()
        assertEquals(articles, vm.state.value.articles)
        assertEquals(ApiFailureKind.NETWORK, vm.state.value.error)
        fail = false; vm.search(); advanceUntilIdle()
        assertEquals(articles, vm.state.value.articles)
        assertNull(vm.state.value.error)
        vm.query("carton")
        assertEquals(articles, vm.state.value.articles)
        advanceUntilIdle()
        assertEquals("Paper and cardboard", vm.state.value.articles.single().name)
    }

    @Test fun savedSearchRestoresAndMissingDetailDoesNotExposeThePreviousArticle() = runTest {
        val saved = SavedStateHandle()
        val source = DummyGuideStore()
        val first = model(source, saved)
        first.query("jar"); first.category(WasteCategory.GLASS); advanceUntilIdle()
        val restored = model(source, saved)
        restored.load(); advanceUntilIdle()
        assertEquals("jar", restored.state.value.query)
        assertEquals(WasteCategory.GLASS, restored.state.value.category)
        val id = restored.state.value.articles.single().id
        restored.detail(id); advanceUntilIdle(); assertNotNull(restored.state.value.detail!!.article)
        restored.detail("removed"); advanceUntilIdle()
        assertNull(restored.state.value.detail!!.article)
        assertEquals(ApiFailureKind.NOT_FOUND, restored.state.value.detail!!.error)
    }

    @Test fun guidePrefillHasNoQuantityOrVersionAndStillRequiresExplicitInput() {
        val date = LocalDate.of(2026, 10, 21)
        val guide = DummyGuideStore.fixtures[1]
        val draft = EditorDraft.forGuide(guide, date)
        assertEquals(guide.category, draft.values.category)
        assertEquals(guide.method, draft.values.method)
        assertEquals("", draft.values.quantity.rawText)
        assertNull(draft.values.version)
        assertFalse(draft.dirty)
        assertNull(draft.prepareSubmission(date))
        assertTrue(EditorField.QUANTITY in draft.errors)
    }

    @Test fun mismatchedDetailIdentityCannotBecomeAnEditorPrefill() = runTest {
        val source = DummyGuideStore()
        val vm = model(object : GuideStore by source {
            override suspend fun get(id: String) = ApiResult.Success(DummyGuideStore.fixtures.last())
        })
        vm.detail(DummyGuideStore.fixtures.first().id); advanceUntilIdle()
        assertNull(vm.state.value.detail!!.article)
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, vm.state.value.detail!!.error)
    }

    @Test fun repeatedPageCursorIsRejectedWithoutDiscardingPreviouslyLoadedArticles() = runTest {
        val source = DummyGuideStore((0..20).map { DummyGuideStore.fixtures.first().copy(id = "guide-$it") })
        val vm = model(object : GuideStore by source {
            override suspend fun search(query: String, category: WasteCategory?, cursor: String?): ApiResult<List<WasteGuide>> =
                if (cursor != null) ApiResult.Success(listOf(DummyGuideStore.fixtures.last()), PageInfo(cursor, true))
                else source.search(query, category, cursor)
        })
        vm.load(); advanceUntilIdle(); vm.search(more = true); advanceUntilIdle()
        assertEquals(20, vm.state.value.articles.size)
        assertEquals("20", vm.state.value.nextCursor)
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, vm.state.value.error)
    }

    @Test fun reviewSourcesRejectExecutableAndCredentialBearingUrls() {
        val date = LocalDate.of(2026, 1, 1)
        assertEquals("example.org", GuideReview(URI("https://example.org/guide"), date).source.host)
        listOf("javascript:alert(1)", "file:///private", "https://user:password@example.org/").forEach { source ->
            assertThrows(IllegalArgumentException::class.java) { GuideReview(URI(source), date) }
        }
    }
}
