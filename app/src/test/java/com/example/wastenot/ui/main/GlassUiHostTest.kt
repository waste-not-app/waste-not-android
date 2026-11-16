package com.example.wastenot.ui.main

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.wastenot.DeleteAccount
import com.example.wastenot.R
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.model.*
import com.example.wastenot.theme.LocalWasteNotScaffoldPadding
import com.example.wastenot.theme.WasteNotTheme
import com.example.wastenot.ui.components.*
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassUiHostTest {
    @get:Rule(order = 0) val fontScale = object : org.junit.rules.ExternalResource() {
        override fun before() { org.robolectric.RuntimeEnvironment.setFontScale(2f) }
        override fun after() { org.robolectric.RuntimeEnvironment.setFontScale(1f) }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()
    private var dark by mutableStateOf(false)

    private fun content(block: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                WasteNotTheme(dark) { Surface(Modifier.fillMaxSize()) { block() } }
            }
        }
        compose.runOnIdle { compose.activity.window.decorView.dispatchWindowFocusChanged(true) }
    }

    private fun readable(label: String, matcher: SemanticsMatcher = hasText(label)) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(matcher).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Missing text layout for $label", layouts.isNotEmpty())
        assertTrue("Clipped label: $label", layouts.none { it.hasVisualOverflow })
    }

    private fun capture(name: String) {
        val directory = System.getenv("WASTENOT_UI_CAPTURE_DIR") ?: return
        val file = File(directory, "$name-${if (dark) "dark" else "light"}.png")
        file.parentFile.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            try {
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun longActionLabelsWrapAndRemainOperableAtLargeText() {
        val labels = listOf("Simpan aktivitas setelah peninjauan", "Lanjutkan tanpa lampiran foto", "Buka sumber panduan pengelolaan")
        val clicked = mutableListOf<Int>()
        content {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(labels[0], { clicked.add(0) }, Modifier.fillMaxWidth())
                SecondaryActionButton(labels[1], { clicked.add(1) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_camera)
                TextActionButton(labels[2], { clicked.add(2) }, Modifier.fillMaxWidth())
            }
        }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; clicked.clear() }
            labels.forEachIndexed { index, label ->
                val action = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                action.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                action.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
                readable(label)
                compose.runOnIdle { assertEquals(index, clicked.last()) }
            }
            capture("actions-font200")
        }
    }

    @Test fun enlargedDockKeepsReadingSpaceAndEveryDestinationReachable() {
        var selected by mutableStateOf(MainTab.HOME)
        var records = 0
        content {
            AccountPage(selected.key, selected, false, 0.dp, false, {}, { selected = it }, { records++ }, {}) {
                Box(Modifier.fillMaxSize().padding(LocalWasteNotScaffoldPadding.current)) {
                    Box(Modifier.fillMaxSize().testTag("reading-viewport"))
                }
            }
        }
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            MainTab.entries.forEach { tab ->
                val label = compose.activity.getString(tab.label)
                compose.onNode(hasText(label) and tabRole).performScrollTo().performClick().assertIsSelected()
                readable(label, hasText(label) and tabRole)
                compose.runOnIdle { assertEquals(tab, selected) }
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                val reading = compose.onNodeWithTag("reading-viewport").fetchSemanticsNode().boundsInRoot
                assertTrue("Chrome consumes more than half the screen", reading.height >= root.height / 2f)
            }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.scan_action_description)).performClick()
            capture("dock-font200")
        }
        compose.runOnIdle { assertEquals(2, records) }
    }

    @Test fun longHeaderGrowsAndBackRemainsReachable() {
        var backs = 0
        content { AccountPage(DeleteAccount, MainTab.PROFILE, false, 0.dp, false, { backs++ }, {}, {}, {}) {} }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            readable(compose.activity.getString(R.string.delete_account_title))
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).assertIsDisplayed().performClick()
            capture("header-font200")
        }
        compose.runOnIdle { assertEquals(2, backs) }
    }

    @Test fun editorSaveIsReachableAndSubmitsEnteredQuantityAtLargeText() {
        val date = LocalDate.of(2026, 11, 16)
        val draft = EditorDraft(EditorValues(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, QuantityInput("250"), date, "", null))
        var saved: ActivityInput? = null
        content { LogEditorScreen(draft, date, "Asia/Jakarta", false, PaddingValues(), { saved = it }, {}, {}, Modifier.fillMaxSize()) }
        compose.onNodeWithText(compose.activity.getString(R.string.save_activity)).performScrollTo().assertIsDisplayed()
        capture("editor-save-font200")
        compose.onNodeWithText(compose.activity.getString(R.string.save_activity)).performClick()
        compose.runOnIdle {
            assertEquals(Quantity.Weight.of(250), saved?.quantity)
            assertEquals(DisposalMethod.RECYCLE, saved?.method)
        }
    }

    @Test fun compactDateInputRejectsOutOfRangeAndAppliesEnteredDate() {
        val date = LocalDate.of(2026, 11, 16)
        var chosen: LocalDate? = null
        content { DateField(null, date.minusDays(30), date, { chosen = it }, Modifier.fillMaxWidth()) }
        compose.onNodeWithText(compose.activity.getString(R.string.choose_filter_date)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("11172026")
        val apply = compose.onNodeWithText(compose.activity.getString(R.string.apply))
        apply.assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("11162026")
        apply.assertIsEnabled()
        capture("date-input-font200")
        apply.performClick()
        compose.runOnIdle { assertEquals(date, chosen) }
    }
}
