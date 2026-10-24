package com.example.wastenot.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.wastenot.LogEditor
import com.example.wastenot.clearEditorDraftFixtures
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.ui.main.*
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocalEditorDraftStoreTest {
    @Before fun reset() = clearEditorDraftFixtures(draftTestContext)
    private fun snapshot() = EditorDraft.from(null, LocalDate.of(2026, 10, 24))
        .stored(LogEditor(null, "10716db1-5dba-4a7e-bef4-69a3445d64ce"), MainTab.HOME)

    @Test fun recreatedAdaptersRestoreOnlyTheirOwnAccountAndClearOnlyTheMatchingRequest() = runTest {
        val a = LocalEditorDraftStore.create(draftTestContext, "account-A")
        val b = LocalEditorDraftStore.create(draftTestContext, "account-B")
        val draft = snapshot()
        assertTrue(a.write(draft) is ApiResult.Success)
        assertNull((b.read() as ApiResult.Success).value)
        assertEquals(draft, (LocalEditorDraftStore.create(draftTestContext, "account-A").read() as ApiResult.Success).value)
        assertTrue(a.clear("different-request") is ApiResult.Success)
        assertEquals(draft, (a.read() as ApiResult.Success).value)
        assertTrue(a.clear(draft.requestId) is ApiResult.Success)
        assertNull((a.read() as ApiResult.Success).value)
    }

    @Test fun failedCommitsNeverClaimSuccessOrReplaceTheConfirmedAndroidPreferenceCache() = runTest {
        val preferences = draftTestContext.getSharedPreferences("editor_draft_commit_fixture", Context.MODE_PRIVATE)
        val good = LocalEditorDraftStore(preferences, "account-A")
        val draft = snapshot()
        assertTrue(good.write(draft) is ApiResult.Success)
        val failing = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val actual = preferences.edit()
                return object : SharedPreferences.Editor by actual {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { actual.putString(key, value) }
                    override fun commit(): Boolean { actual.commit(); return false }
                }
            }
        }
        val broken = LocalEditorDraftStore(failing, "account-A")
        assertTrue(broken.write(draft.copy(fields = draft.fields.toMutableList().apply { this[12] = "Unconfirmed" })) is ApiResult.Failure)
        assertEquals(draft, (good.read() as ApiResult.Success).value)
        assertTrue(broken.clear() is ApiResult.Failure)
        assertEquals(draft, (good.read() as ApiResult.Success).value)
    }

    @Test fun corruptPreferenceTypesRemainAnErrorUntilExplicitlyCleared() = runTest {
        val preferences = draftTestContext.getSharedPreferences("editor_draft_corrupt_fixture", Context.MODE_PRIVATE)
        check(preferences.edit().putInt("draft", 7).commit())
        val store = LocalEditorDraftStore(preferences, "account-A")
        assertTrue(store.read() is ApiResult.Failure)
        assertEquals(7, preferences.getInt("draft", -1))
        assertTrue(store.clear() is ApiResult.Success)
        assertNull((store.read() as ApiResult.Success).value)
    }
}
