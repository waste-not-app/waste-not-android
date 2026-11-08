package com.example.wastenot.theme

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.TextActionButton

@Composable
internal fun AppearanceProblemBanner(failure: ThemeFailure, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MessageBanner(stringResource(if (failure == ThemeFailure.READ) R.string.theme_read_failed else R.string.theme_write_failed),
            Modifier.weight(1f), isError = true)
        TextActionButton(stringResource(R.string.retry), onRetry)
    }
}
