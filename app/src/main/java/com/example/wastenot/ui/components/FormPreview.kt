package com.example.wastenot.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wastenot.model.QuantityInput
import com.example.wastenot.theme.WasteNotTheme

@Preview(name = "Input terang", widthDp = 360)
@Preview(name = "Input gelap", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Input 200%", widthDp = 360, fontScale = 2f)
@Composable
private fun FormPreview() {
    var input by remember { mutableStateOf(QuantityInput()) }
    WasteNotTheme {
        Surface {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WasteTextField("", {}, "Email", Modifier.fillMaxWidth(), helper = "nama@contoh.id")
                WasteTextField("", {}, "Kata sandi", Modifier.fillMaxWidth(), errorText = "Gunakan 12–128 karakter.", password = true)
                WasteTextField("Pengguna Demo", {}, "Nama", Modifier.fillMaxWidth(), enabled = false)
                CategoryPicker(null, {}, Modifier.fillMaxWidth())
                MethodSelector(null, {}, Modifier.fillMaxWidth())
                QuantityField(input, { input = it }, Modifier.fillMaxWidth())
                ActionButton("Simpan", {}, Modifier.fillMaxWidth(), loading = true, loadingLabel = "Menyimpan…")
                ActionButton("Hapus catatan", {}, Modifier.fillMaxWidth(), destructive = true)
            }
        }
    }
}
