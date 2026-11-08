package com.example.wastenot.ui.main

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.R
import com.example.wastenot.data.dummy.DummyGuideStore
import com.example.wastenot.model.User
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.WasteNotTheme
import com.example.wastenot.ui.components.*
import java.io.File
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

class KeyboardFocusDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keyboardFocusOnActionsHasReviewFramesInBothThemes() {
        var dark by mutableStateOf(false)
        compose.setContent {
            WasteNotTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        ActionButton("Simpan catatan", {}, Modifier.fillMaxWidth().testTag("primary"))
                        SecondaryActionButton("Batal", {}, Modifier.fillMaxWidth().testTag("secondary"))
                        TextActionButton("Lihat riwayat", {}, Modifier.testTag("text"))
                        IconActionButton(R.drawable.ic_close, "Tutup", {}, Modifier.testTag("icon"))
                        RecordAction({}, Modifier.testTag("record"))
                    }
                }
            }
        }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            keyboardMode()
            compose.onNodeWithTag("primary").performSemanticsAction(SemanticsActions.RequestFocus)
            for ((index, tag) in listOf("primary", "secondary", "text", "icon", "record").withIndex()) {
                if (index > 0) compose.onRoot().performKeyInput { pressKey(Key.Tab) }
                compose.onNodeWithTag(tag).assertIsFocused()
                capture("action-$tag-${if (theme) "dark" else "light"}", compose.onNodeWithTag(tag))
            }
        }
    }

    @Test fun keyboardTabsDoNotSelectUntilEnterAndScanReturnsFocusToItsTrigger() {
        val user = User("keyboard-focus-review", "Pengguna Uji", "focus@example.test", ZoneId.of("Asia/Jakarta"), null)
        val activities = com.example.wastenot.navigationActivityFixture(
            InstrumentationRegistry.getInstrumentation().targetContext, user)
        val guides = DummyGuideStore()
        var dark by mutableStateOf(false)
        compose.setContent {
            WasteNotTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().background(LocalWasteNotGradients.current.canvas)) {
                    AccountNavigation(user, activities, guides, { com.example.wastenot.data.dummy.DummyScanRepository() }, if (dark) ThemeMode.DARK else ThemeMode.LIGHT, {}, {}, Modifier.fillMaxSize())
                }
                }
            }
        }
        val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.waitUntil(20_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            compose.onNode(tabs and hasText("Beranda")).performClick()
            keyboardMode()
            compose.onNode(tabs and hasText("Beranda")).performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onRoot().performKeyInput { pressKey(Key.Tab) }
            compose.onNode(tabs and hasText("Riwayat")).assertIsFocused().assertIsNotSelected()
            compose.onNode(tabs and hasText("Beranda")).assertIsSelected()
            capture("navigation-${if (theme) "dark" else "light"}", compose.onNode(tabs and hasText("Riwayat")))
            compose.onRoot().performKeyInput { pressKey(Key.Enter) }
            compose.onNode(tabs and hasText("Riwayat")).assertIsSelected()
            val record = compose.onNodeWithContentDescription("Catat atau scan sampah")
            record.performSemanticsAction(SemanticsActions.RequestFocus)
            record.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
            compose.onNodeWithText("Scan sampah").assertIsDisplayed()
            compose.onNodeWithContentDescription("Tutup scan").performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onNodeWithContentDescription("Tutup scan").performKeyInput { pressKey(Key.Enter) }
            record.assertIsFocused()
            compose.onNode(tabs and hasText("Riwayat")).assertIsSelected()
            capture("returned-${if (theme) "dark" else "light"}", record)
        }
    }

    private fun capture(name: String, node: SemanticsNodeInteraction) {
        node.assertIsFocused()
        captureUiPreview(compose, name, "keyboard-focus")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bounds = node.fetchSemanticsNode().boundsInWindow
        File(context.getExternalFilesDir(null), "keyboard-focus/$name-bounds.json").writeText(JSONObject()
            .put("left", bounds.left).put("top", bounds.top).put("right", bounds.right).put("bottom", bounds.bottom)
            .put("density", context.resources.displayMetrics.density).toString())
    }

    private fun keyboardMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitUntil(5_000) {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == instrumentation.targetContext.packageName
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        compose.waitForIdle()
    }
}
