package com.example.wastenot.ui.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.wastenot.R
import com.example.wastenot.data.local.ReminderSaveResult
import com.example.wastenot.data.local.ReminderSetting
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.Achievement
import com.example.wastenot.model.AchievementRequirement
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog
import com.example.wastenot.theme.WasteNotTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RemainingFeaturesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun achievementsScreenRendersItemsExpandsRequirementsAndHandlesErrorRetry() {
        var retried = false
        val items = listOf(
            Achievement(
                id = "00000000-0000-4000-8000-000000000101",
                code = "FIRST_STEP",
                title = "First step",
                description = "Log at least 1 active activity.",
                requirementType = AchievementRequirement.ACTIVE_LOG_COUNT,
                currentValue = 1,
                targetValue = 1,
                isUnlocked = true,
                unlockedAt = Instant.parse("2026-11-13T07:00:00Z"),
            ),
            Achievement(
                id = "00000000-0000-4000-8000-000000000102",
                code = "STREAK_3",
                title = "Three days in a row",
                description = "Reach a best streak of 3 days.",
                requirementType = AchievementRequirement.BEST_STREAK,
                currentValue = 2,
                targetValue = 3,
                isUnlocked = false,
                unlockedAt = null,
            ),
        )

        compose.setContent {
            WasteNotTheme {
                AchievementsScreen(
                    state = AchievementsUiState(items = items),
                    accountTimezone = ZoneId.of("Asia/Jakarta"),
                    padding = PaddingValues(),
                    onRetry = { retried = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        compose.onNodeWithText("First step").assertIsDisplayed()
        compose.onNodeWithText("Unlocked").assertIsDisplayed()
        compose.onNodeWithText("Three days in a row").assertIsDisplayed()
        compose.onNodeWithText("Locked").assertIsDisplayed()
        compose.onNodeWithText("2 of 3").assertIsDisplayed()
        compose.onNodeWithText("Reach a best streak of 3 days.").assertDoesNotExist()
        compose.onNodeWithText("Three days in a row").performClick()
        compose.onNodeWithText("Reach a best streak of 3 days.").assertIsDisplayed()
    }

    @Test
    fun achievementsScreenFailureAllowsRetry() {
        var retried = false
        compose.setContent {
            WasteNotTheme {
                AchievementsScreen(
                    state = AchievementsUiState(items = null, loading = false, error = ApiFailureKind.NETWORK),
                    accountTimezone = ZoneId.of("Asia/Jakarta"),
                    padding = PaddingValues(),
                    onRetry = { retried = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.onNodeWithText("Couldn't load achievements.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertTrue(retried)
    }

    @Test
    fun reminderSettingsShowsApproximateTimeAndSavesSelection() {
        var saved: ReminderSetting? = null
        compose.setContent {
            WasteNotTheme {
                ReminderSettingsScreen(
                    setting = ReminderSetting(enabled = true, hour = 19, minute = 30),
                    padding = PaddingValues(),
                    onSave = {
                        saved = it
                        ReminderSaveResult.SAVED
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        compose.onNodeWithText("Around 19:30").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Save reminder").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ReminderSetting(enabled = true, hour = 19, minute = 30), saved) }
        compose.onNodeWithText("Daily reminder saved for around 19:30.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reminderSettingsDisablesTimeButtonWhenOff() {
        compose.setContent {
            WasteNotTheme {
                ReminderSettingsScreen(
                    setting = ReminderSetting(enabled = false, hour = 20, minute = 0),
                    padding = PaddingValues(),
                    onSave = { ReminderSaveResult.SAVED },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.onNodeWithText("Around 20:00").assertIsNotEnabled()
    }

    @Test
    fun editorShowsOptionalPhotoControlsAndAllowsRemovingWithoutBlockingSave() {
        val today = LocalDate.of(2026, 11, 13)
        val draft = EditorDraft.from(
            WasteLog(
                id = "00000000-0000-4000-8000-000000000001",
                category = WasteCategory.PLASTIC,
                method = DisposalMethod.RECYCLE,
                quantity = Quantity.Count.of(1),
                activityDate = today,
                activityTimezone = ZoneId.of("Asia/Jakarta"),
                notes = "",
                version = 1,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                photoId = "00000000-0000-4000-8000-000000000201",
            ),
            today,
        )
        var removed = false
        var dismissed = false

        compose.setContent {
            WasteNotTheme {
                LogEditorScreen(
                    draft = draft,
                    referenceDate = today,
                    accountTimezone = "Asia/Jakarta",
                    saving = false,
                    padding = PaddingValues(),
                    onSave = {},
                    onHistory = {},
                    onResolveConflict = {},
                    modifier = Modifier.fillMaxSize(),
                    photosEnabled = true,
                    photoError = R.string.photo_error_upload,
                    onRemovePhoto = {
                        removed = true
                        draft.removePhoto()
                    },
                    onDismissPhotoError = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText("Photo attachment (optional)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue without a photo").performScrollTo().performClick()
        assertTrue(dismissed)
        compose.onNodeWithText("Remove photo").performScrollTo().performClick()
        assertTrue(removed)
        compose.onNodeWithText("Choose photo").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detailScreenShowsPhotoRetryWhenAttachmentLoadFails() {
        val log = WasteLog(
            id = "00000000-0000-4000-8000-000000000001",
            category = WasteCategory.PLASTIC,
            method = DisposalMethod.RECYCLE,
            quantity = Quantity.Count.of(1),
            activityDate = LocalDate.of(2026, 11, 13),
            activityTimezone = ZoneId.of("Asia/Jakarta"),
            notes = "",
            version = 1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            photoId = "00000000-0000-4000-8000-000000000201",
        )
        var retried = false
        compose.setContent {
            WasteNotTheme {
                LogDetailScreen(
                    log = log,
                    loading = false,
                    loadError = false,
                    padding = PaddingValues(),
                    onEdit = {},
                    onDelete = {},
                    onHistory = {},
                    onRetry = {},
                    modifier = Modifier.fillMaxSize(),
                    photosEnabled = true,
                    photoError = true,
                    onRetryPhoto = { retried = true },
                )
            }
        }

        compose.onNodeWithText("Photo attachment").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Couldn't load the photo attachment.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        assertTrue(retried)
    }
}
