package com.example.wastenot.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.theme.WasteNotWordmark
import com.example.wastenot.theme.Pine900

@Composable
fun BrandWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.wastenot_brand_mark),
            contentDescription = null,
            modifier = Modifier
                .background(Pine900, RoundedCornerShape(10.dp))
                .padding(5.dp)
                .width(34.dp)
                .aspectRatio(773f / 546f),
        )
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.weight(1f, fill = false),
            style = WasteNotWordmark,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
