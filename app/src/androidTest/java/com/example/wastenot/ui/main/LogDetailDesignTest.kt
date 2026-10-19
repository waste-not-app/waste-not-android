package com.example.wastenot.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.*
import com.example.wastenot.theme.WasteNotTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LogDetailDesignTest {
    @get:Rule val compose = createComposeRule()
    private val log = WasteLog("detail", WasteCategory.METAL, DisposalMethod.RECYCLE, Quantity.Weight.of(99_999),
        LocalDate.of(2026, 10, 19), ZoneId.of("Asia/Jakarta"), "Catatan pribadi", 1, Instant.EPOCH, Instant.EPOCH)

    @Test fun largestValuesKeepTheirDigitsTogetherAndCacheErrorsDisableWrites() {
        val quantity = mutableStateOf<Quantity>(log.quantity)
        val error = mutableStateOf(false)
        compose.setContent { WasteNotTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                LogDetailScreen(log.copy(quantity = quantity.value), false, error.value, PaddingValues(), {}, {}, {}, {})
            }
        } }
        listOf(Quantity.Weight.of(99_999), Quantity.Count.of(10_000)).forEach { value ->
            compose.runOnIdle { quantity.value = value }
            val formatted = WasteFormat.quantity(value)
            val label = formatted.amount + if (value is Quantity.Weight) " kg" else " benda"
            val text = compose.onNodeWithText(label).performScrollTo()
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.single().didOverflowWidth)
            assertEquals(1, formatted.amount.indices.map { layouts.single().getLineForOffset(it) }.toSet().size)
        }
        compose.runOnIdle { error.value = true }
        compose.onNodeWithText("Ubah catatan").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Hapus catatan").performScrollTo().assertIsNotEnabled()
    }

    @Test fun conflictRequiresReloadAndStorageFailureCanRetryWithoutAutoDelete() {
        val error = mutableStateOf<ApiFailureKind?>(ApiFailureKind.VERSION_CONFLICT)
        var confirmations = 0
        var reloads = 0
        compose.setContent { WasteNotTheme {
            DeleteLogDialog(log, false, error.value, {}, { confirmations++ }, { reloads++ }, {})
        } }
        listOf(ApiFailureKind.VERSION_CONFLICT, ApiFailureKind.PRECONDITION_REQUIRED).forEach { kind ->
            compose.runOnIdle { error.value = kind }
            compose.onNodeWithText("Hapus", substring = false).assertDoesNotExist()
            compose.onNodeWithText("Muat versi terbaru").performScrollTo().performClick()
        }
        assertEquals(2, reloads)
        assertEquals(0, confirmations)
        compose.runOnIdle { error.value = ApiFailureKind.STORAGE }
        compose.onNodeWithText("Catatan belum dihapus.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hapus", substring = false).performScrollTo().performClick()
        assertEquals(1, confirmations)
    }

    @Test fun modalActionsAreStackedAndBusyPreventsBothActions() {
        val busy = mutableStateOf(false)
        var confirmations = 0
        var cancellations = 0
        compose.setContent { WasteNotTheme {
            DeleteLogDialog(log, busy.value, null, { cancellations++ }, { confirmations++ }, {}, {})
        } }
        val confirm = compose.onNodeWithText("Hapus", substring = false)
        val cancel = compose.onNodeWithText("Batal")
        confirm.performScrollTo().assertHeightIsAtLeast(48.dp)
        cancel.performScrollTo().assertHeightIsAtLeast(48.dp)
        val confirmBounds = confirm.getUnclippedBoundsInRoot()
        val cancelBounds = cancel.getUnclippedBoundsInRoot()
        assertEquals((confirmBounds.right - confirmBounds.left).value, (cancelBounds.right - cancelBounds.left).value, .5f)
        assertTrue(cancelBounds.top >= confirmBounds.bottom)
        cancel.performClick()
        assertEquals(1, cancellations)
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithText("Menghapus…").performScrollTo().assertIsNotEnabled()
        cancel.performScrollTo().assertIsNotEnabled()
        assertEquals(0, confirmations)
    }

    @Test fun unknownDetailOffersHistoryWithoutInventingEditableValues() {
        var history = 0
        compose.setContent { WasteNotTheme {
            LogDetailScreen(null, false, false, PaddingValues(), {}, {}, { history++ }, {}, Modifier.fillMaxSize())
        } }
        compose.onNodeWithText("Catatan tidak tersedia").assertIsDisplayed()
        compose.onNodeWithText("Ubah catatan").assertDoesNotExist()
        compose.onNodeWithText("Hapus catatan").assertDoesNotExist()
        compose.onNodeWithText("Lihat riwayat").performClick()
        assertEquals(1, history)
    }
}
