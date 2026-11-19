package com.example.wastenot.ui.scan

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.R
import com.example.wastenot.model.*
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScanChoiceOrderTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val high = AiDetectionObject("first", 39, "bottle", .91, ScanBounds(.1f, .1f, .5f, .5f),
        AiConfidenceBand.OFFERED, true, true, listOf("hdpe_bottle", "pet_bottle"), emptyList(), emptyList(), null)
    private val lower = high.copy(id = "second", classId = 41, rawClass = "cup", confidence = .6, band = AiConfidenceBand.UNCERTAIN,
        suggestedObjectKeys = listOf("plastic_01", "other_specific"))
    private fun items() = (0 until 81).map {
        AiCatalogItem("plastic_%02d".format(it), "Plastic option %02d".format(it), WasteCategory.PLASTIC, false, AiGuideStatus.UNAVAILABLE)
    } + listOf(AiCatalogItem("pet_bottle", "PET bottle", WasteCategory.PLASTIC, false, AiGuideStatus.PUBLISHED),
        AiCatalogItem("hdpe_bottle", "HDPE bottle", WasteCategory.PLASTIC, false, AiGuideStatus.PUBLISHED),
        AiCatalogItem("other_fallback", "Mixed waste", WasteCategory.OTHER, false, AiGuideStatus.UNAVAILABLE),
        AiCatalogItem("other_specific", "Other utensils", WasteCategory.OTHER, false, AiGuideStatus.UNAVAILABLE))

    @Test fun matchingChoicesStayFirstAndCorrectionWorksInLight() = verify(false)
    @Test fun matchingChoicesStayFirstAndCorrectionWorksInDark() = verify(true)

    private fun verify(dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        val catalog = AiCatalog("ordering-fixture", items(), "fixture", "fixture", "fixture", emptyMap(), emptySet(),
            mapOf("bottle" to "Bottle", "cup" to "Cup"))
        assertEquals(85, catalog.items.size)
        val state = mutableStateOf(ScanState(session = "order-$mode", phase = ScanPhase.OBJECT,
            objects = listOf(high, lower), selectedId = high.id, catalog = catalog))
        var confirmations = 0
        compose.setContent {
            WasteNotTheme(darkTheme = dark) {
                ScanSheet(state.value, {}, {}, {}, {}, { id -> state.value = state.value.copy(selectedId = id, category = null, objectKey = null) },
                    { category -> state.value = state.value.copy(category = category, objectKey = null) },
                    { confirmations++ }, {}, {}, {}, {}, {}, {},
                    onObjectKey = { key -> state.value = state.value.copy(objectKey = key) }, camera = {})
            }
        }
        val first = compose.onNode(hasText("Bottle") and isSelectable())
        val second = compose.onNode(hasText("Cup") and isSelectable())
        first.assertIsSelected(); second.assertIsNotSelected()
        assertTrue(first.fetchSemanticsNode().boundsInRoot.top < second.fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText(context.getString(R.string.scan_uncertain)).assertExists()
        capture("candidates-$mode")
        chooseMaterial("Plastic")
        openTypes()
        menu("HDPE bottle").assertIsDisplayed()
        menu("PET bottle").assertIsDisplayed()
        capture("suggested-types-$mode")
        assertTrue("Server hint order must put HDPE before PET", menu("HDPE bottle").fetchSemanticsNode().boundsInRoot.top < menu("PET bottle").fetchSemanticsNode().boundsInRoot.top)
        assertTrue(menu("PET bottle").fetchSemanticsNode().boundsInRoot.top < menu("Plastic option 00").fetchSemanticsNode().boundsInRoot.top)
        assertNull(state.value.objectKey); assertEquals(0, confirmations)
        menu("Plastic option 80").performScrollTo().performClick()
        assertEquals("plastic_80", state.value.objectKey)
        capture("manual-correction-$mode")

        chooseMaterial("Other")
        chooseMaterial("Plastic")
        assertNull(state.value.objectKey)
        openTypes()
        menu("HDPE bottle").assertIsDisplayed()
        menu("PET bottle").assertIsDisplayed()
        assertTrue(menu("HDPE bottle").fetchSemanticsNode().boundsInRoot.top < menu("PET bottle").fetchSemanticsNode().boundsInRoot.top)
        capture("material-reset-top-$mode")
        menu("Plastic option 00").performClick()
        second.performScrollTo().performClick()
        assertEquals(lower.id, state.value.selectedId)
        assertNull(state.value.category); assertNull(state.value.objectKey)
        chooseMaterial("Plastic")
        openTypes()
        menu("Plastic option 01").assertIsDisplayed()
        assertTrue(menu("Plastic option 01").fetchSemanticsNode().boundsInRoot.top < menu("Plastic option 00").fetchSemanticsNode().boundsInRoot.top)
        capture("changed-candidate-$mode")
        menu("Plastic option 01").performClick()
        assertEquals("plastic_01", state.value.objectKey)
        compose.onNodeWithText(context.getString(R.string.scan_confirm_object)).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, confirmations)
    }

    private fun menu(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isPopup()))
    private fun openTypes() { compose.onNodeWithContentDescription(context.getString(R.string.scan_object_type)).performScrollTo().performClick() }
    private fun chooseMaterial(label: String) {
        compose.onNodeWithContentDescription(context.getString(R.string.scan_material)).performScrollTo().performClick()
        menu(label).performClick()
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle(); instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        instrumentation.uiAutomation.takeScreenshot()?.recycle(); android.os.SystemClock.sleep(200)
        assertEquals(context.packageName, instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(context.cacheDir, "qa/scan-choice-order/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
