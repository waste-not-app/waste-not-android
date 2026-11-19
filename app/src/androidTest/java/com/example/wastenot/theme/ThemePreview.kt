package com.example.wastenot.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Light", widthDp = 360)
@Preview(name = "Dark", widthDp = 360, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Teks 200%", widthDp = 360, fontScale = 2f)
@Composable
internal fun ThemePreview() {
    WasteNotTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("WasteNot", style = MaterialTheme.typography.headlineLarge)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalWasteNotGradients.current.summary, MaterialTheme.shapes.large)
                        .padding(24.dp),
                ) {
                    Text("Sample data", color = SummarySecondaryText)
                    Text("650 g", style = MaterialTheme.typography.displayLarge, color = SummaryText)
                }
                Text("Waste handled today", style = MaterialTheme.typography.titleMedium)
                Text("Reduce dicatat terpisah.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField("0,25", {}, label = { Text("Weight (kg)") })
                Button(onClick = {}) { Text("Log activity") }
                listOf(
                    Typography.bodyLarge, Typography.titleMedium, Typography.titleLarge,
                    Typography.headlineLarge, Typography.displaySmall, Typography.displayMedium,
                ).forEach { Text("250 g · 2 items", style = it) }
            }
        }
    }
}
