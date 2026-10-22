package com.example.wastenot.ui.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog
import com.example.wastenot.theme.WasteNotTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class SummaryPrecisionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun handledAndAvoidedSummaryWeightsUseTwoDecimalPlaces() {
        val date = LocalDate.of(2026, 9, 21)
        val record = WasteLog("00000000-0000-4000-8000-000000000001", WasteCategory.PLASTIC,
            DisposalMethod.RECYCLE, Quantity.Weight.of(1655), date, ZoneId.of("Asia/Jakarta"),
            "", 1, Instant.EPOCH, Instant.EPOCH)
        val state = WorkspaceUiState(loading = false, records = listOf(record,
            record.copy(id = "00000000-0000-4000-8000-000000000002", method = DisposalMethod.REDUCE)))
        compose.setContent { WasteNotTheme(darkTheme = false) {
            StatisticsScreen(state, demo = true, date = date, padding = PaddingValues(0.dp), onRetry = {}, onRecord = {})
        } }
        compose.onAllNodes(androidx.compose.ui.test.hasText("1,66 kg", substring = true) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("statistics-summary"))).assertCountEquals(2)
        compose.onNodeWithText("1,655 kg", substring = true).assertDoesNotExist()
    }
}
