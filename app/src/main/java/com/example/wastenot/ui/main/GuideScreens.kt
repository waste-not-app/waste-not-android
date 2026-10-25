package com.example.wastenot.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteFormat
import com.example.wastenot.model.WasteGuide
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.labelResource
import kotlinx.coroutines.launch

@Composable
fun GuideListScreen(
    data: GuideUiState,
    padding: PaddingValues,
    onQuery: (String) -> Unit,
    onCategory: (WasteCategory?) -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    onDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchAction = remember { FocusRequester() }
    fun resetScroll() { scope.launch { list.scrollToItem(0) } }
    fun submit() { searchAction.requestFocus(); keyboard?.hide(); onSearch(); resetScroll() }
    fun clear() { focus.clearFocus(); keyboard?.hide(); onQuery(""); onCategory(null); resetScroll() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LazyColumn(Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth(), state = list,
            contentPadding = pagePadding(padding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "intro") { Text(stringResource(R.string.guide_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item(key = "search") {
                val label = stringResource(R.string.guide_search)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(data.query, { onQuery(it); resetScroll() }, Modifier.fillMaxWidth().semantics { contentDescription = label },
                        placeholder = { Text(stringResource(R.string.guide_search_hint)) }, singleLine = true, shape = MaterialTheme.shapes.small,
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface),
                        trailingIcon = if (data.query.isNotEmpty()) ({ IconButton(onClick = { onQuery(""); resetScroll() }) {
                            Icon(painterResource(R.drawable.ic_clear), stringResource(R.string.guide_clear))
                        } }) else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SecondaryActionButton(stringResource(R.string.guide_search_action), ::submit,
                            Modifier.focusProperties { canFocus = true }.focusRequester(searchAction), icon = R.drawable.ic_search)
                        TextButton(onClick = ::clear, enabled = data.query.isNotEmpty() || data.category != null,
                            colors = ButtonDefaults.textButtonColors(disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.guide_clear_action)) }
                        GuideCategory(data.category, { onCategory(it); resetScroll() })
                    }
                }
            }
            if (data.articles.any { it.review == null }) item(key = "editorial") {
                GuideEditorialNotice(stringResource(R.string.guide_catalog_pending))
            }
            if (data.loading) item(key = "loading") {
                Text(stringResource(R.string.guide_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            if (!data.loading && data.articles.isEmpty()) item(key = "empty") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(if (data.error != null) R.string.guide_error else R.string.guide_empty_title), style = MaterialTheme.typography.headlineSmall)
                    if (data.error == null) Text(stringResource(R.string.guide_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ActionButton(stringResource(if (data.error != null) R.string.retry else R.string.guide_clear),
                        if (data.error != null) onSearch else ::clear)
                }
            }
            items(data.articles, key = { it.id }) { article ->
                GuideRow(article, { keyboard?.hide(); onDetail(article.id) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (data.articles.isNotEmpty() && data.error != null) item(key = "page-error") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MessageBanner(stringResource(R.string.guide_error), isError = true)
                    if (data.nextCursor == null) ActionButton(stringResource(R.string.retry), onSearch)
                }
            }
            if (data.nextCursor != null) item(key = "more") {
                ActionButton(stringResource(if (data.error != null) R.string.retry else R.string.guide_more), onMore,
                    loading = data.loadingMore, loadingLabel = stringResource(R.string.guide_loading))
            }
        }
    }
}

@Composable
private fun GuideCategory(category: WasteCategory?, onCategory: (WasteCategory?) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(selected = category != null, onClick = { expanded = true }, label = {
            Text(category?.let { stringResource(it.labelResource) } ?: stringResource(R.string.guide_all_categories))
        }, shape = androidx.compose.foundation.shape.CircleShape,
            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant, selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer), modifier = Modifier.heightIn(min = 48.dp))
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.guide_all_categories)) }, onClick = { expanded = false; onCategory(null) })
            WasteCategory.entries.forEach { option ->
                DropdownMenuItem(text = { Text(stringResource(option.labelResource)) }, onClick = { expanded = false; onCategory(option) })
            }
        }
    }
}

@Composable
private fun GuideRow(article: WasteGuide, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rowFocus = remember { FocusRequester() }
    Row(modifier.fillMaxWidth().focusProperties { canFocus = true }.focusRequester(rowFocus)
        .clickable { rowFocus.requestFocus(); onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
            Icon(painterResource(article.category.iconResource), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(article.name, style = MaterialTheme.typography.titleMedium)
            Text(if (article.review == null) stringResource(R.string.guide_method_draft, stringResource(article.method.labelResource)) else stringResource(article.method.labelResource),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(article.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_next), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GuideDetailScreen(
    data: GuideDetailState?,
    padding: PaddingValues,
    onRetry: () -> Unit,
    onList: () -> Unit,
    onRecord: () -> Unit,
    onSource: (String) -> Unit,
    modifier: Modifier = Modifier,
    sourceUnavailable: Boolean = false,
) {
    val article = data?.article
    ScrollPage(padding, modifier, maxWidth = 560.dp) {
        when {
            data == null || data.loading -> {
                Text(stringResource(R.string.guide_loading))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            article == null || data.error != null -> {
                val missing = data.error == ApiFailureKind.NOT_FOUND || data.error == ApiFailureKind.DELETED
                Text(stringResource(if (missing) R.string.guide_unavailable else R.string.guide_error), style = MaterialTheme.typography.headlineSmall)
                ActionButton(stringResource(if (missing) R.string.guide_back_list else R.string.retry), if (missing) onList else onRetry)
            }
            else -> {
                Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                    Icon(painterResource(article.category.iconResource), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(article.category.labelResource), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                    Text(stringResource(article.method.labelResource), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                }
                Text(article.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                Text(article.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.guide_steps), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                article.steps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                            Text(WasteFormat.integer(index.toLong() + 1), Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                        }
                        Text(step, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (article.cautions.isNotEmpty()) {
                    Text(stringResource(R.string.guide_cautions), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    article.cautions.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                val review = article.review
                if (review == null) GuideEditorialNotice(stringResource(R.string.guide_review_pending), title = stringResource(R.string.guide_editorial_draft)) else {
                    Text(stringResource(R.string.guide_sources), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    TextButton(onClick = { onSource(review.source.toASCIIString()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(review.source.host) }
                    Text(stringResource(R.string.guide_reviewed, WasteFormat.activityDate(review.date)), style = MaterialTheme.typography.bodySmall)
                }
                if (sourceUnavailable) MessageBanner(stringResource(R.string.guide_source_error), isError = true)
                ActionButton(stringResource(R.string.guide_record), onRecord, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GuideEditorialNotice(message: String, modifier: Modifier = Modifier, title: String? = null) {
    Surface(modifier, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
            Text(message, style = if (title == null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
        }
    }
}
