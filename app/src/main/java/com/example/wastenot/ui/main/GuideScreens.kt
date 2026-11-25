package com.example.wastenot.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.wastenot.ui.components.AsyncStatePanel
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.TextActionButton
import com.example.wastenot.ui.components.labelResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import com.example.wastenot.ui.components.CategoryIconBadge
import com.example.wastenot.ui.components.GroupSectionLabel
import com.example.wastenot.ui.components.InsetGroupCard
import com.example.wastenot.ui.components.InsetGroupItemContainer
import com.example.wastenot.ui.components.wasteTextFieldColors
import com.example.wastenot.ui.components.focusOutline

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
    var displayedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var displayedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(data.resultsFor) {
        val committed = data.resultsFor ?: return@LaunchedEffect
        val categoryName = committed.category?.name
        if (displayedQuery != null && (displayedQuery != committed.query || displayedCategory != categoryName)) {
            list.requestScrollToItem(0)
        }
        displayedQuery = committed.query
        displayedCategory = categoryName
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchAction = remember { FocusRequester() }
    fun submit() { searchAction.requestFocus(); keyboard?.hide(); onSearch() }
    fun clear() { focus.clearFocus(); keyboard?.hide(); onQuery(""); onCategory(null); onSearch() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LazyColumn(Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth(), state = list,
            contentPadding = pagePadding(padding)) {
            item(key = "search") {
                val label = stringResource(R.string.guide_search)
                InsetGroupCard(Modifier.padding(bottom = 16.dp), contentPadding = true) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.guide_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        data.query,
                        onQuery,
                        Modifier.fillMaxWidth().semantics { contentDescription = label },
                        placeholder = {
                            Text(stringResource(R.string.guide_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium)
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        colors = wasteTextFieldColors(),
                        trailingIcon = if (data.query.isNotEmpty()) ({
                            IconButton(onClick = { onQuery("") }) {
                                Icon(painterResource(R.drawable.ic_clear), stringResource(R.string.guide_clear))
                            }
                        }) else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SecondaryActionButton(stringResource(R.string.guide_search_action), ::submit,
                            Modifier.focusProperties { canFocus = true }.focusRequester(searchAction), icon = R.drawable.ic_search)
                        GuideCategory(data.category, onCategory)
                        if (data.query.isNotEmpty() || data.category != null) {
                            TextActionButton(stringResource(R.string.guide_clear_action), ::clear)
                        }
                    }
                }
            }
            val hasRows = data.articles.isNotEmpty()
            if (hasRows && (data.showingPreviousResults || data.loading)) item(key = "result-status") {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(if (data.showingPreviousResults) R.string.guide_previous_results else R.string.guide_loading),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (data.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (!hasRows && data.loading) item(key = "loading") {
                AsyncStatePanel(stringResource(R.string.guide_loading), R.drawable.ic_clock,
                    Modifier.fillMaxWidth(), loading = true)
            }
            if (!hasRows && !data.loading && !data.searchPending) item(key = "empty") {
                val filtered = data.query.isNotBlank() || data.category != null
                AsyncStatePanel(
                    title = stringResource(if (data.error != null) guideErrorText(data.error) else R.string.guide_empty_title),
                    icon = if (data.error != null) R.drawable.ic_alert else R.drawable.ic_guide,
                    modifier = Modifier.fillMaxWidth(),
                    description = when {
                        data.error == ApiFailureKind.RATE_LIMITED -> null
                        data.error != null -> loadFailureHint(data.error)
                        else -> stringResource(if (filtered) R.string.guide_empty_message else R.string.guide_catalog_empty_hint)
                    },
                ) {
                    ActionButton(stringResource(when {
                        data.error != null -> R.string.retry
                        filtered -> R.string.guide_clear
                        else -> R.string.guide_reload
                    }), if (data.error != null || !filtered) onSearch else ::clear, Modifier.fillMaxWidth())
                }
            }
            if (hasRows && data.error != null && data.nextCursor == null) item(key = "search-error") {
                Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MessageBanner(stringResource(guideErrorText(data.error)), isError = true)
                    ActionButton(stringResource(R.string.retry), onSearch)
                }
            }
            if (data.articles.isNotEmpty()) itemsIndexed(data.articles, key = { _, article -> article.id }) { idx, article ->
                InsetGroupItemContainer(
                    isFirst = idx == 0,
                    isLast = idx == data.articles.lastIndex,
                ) {
                    GuideRow(article, { keyboard?.hide(); onDetail(article.id) })
                    if (idx < data.articles.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            if (hasRows && data.error != null && data.nextCursor != null) item(key = "page-error") {
                MessageBanner(stringResource(guideErrorText(data.error)), isError = true, modifier = Modifier.padding(top = 16.dp))
            }
            if (data.nextCursor != null) item(key = "more") {
                ActionButton(stringResource(if (data.error != null) R.string.retry else R.string.guide_more), onMore,
                    Modifier.padding(top = 16.dp),
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
        }, shape = MaterialTheme.shapes.small,
            trailingIcon = { Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp)) },
            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant, selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant, selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer),
            modifier = Modifier.heightIn(min = 52.dp))
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
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactions, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .focusOutline(focused, RoundedCornerShape(12.dp), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIconBadge(article.category, article.category.iconResource, sizeDp = 44)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(article.name, style = MaterialTheme.typography.titleMedium)
            Text(if (article.review == null) stringResource(R.string.guide_method_draft, stringResource(article.method.labelResource)) else stringResource(article.method.labelResource),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(article.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                AsyncStatePanel(stringResource(R.string.guide_loading), R.drawable.ic_clock,
                    Modifier.fillMaxWidth(), loading = true)
            }
            article == null || data.error != null -> {
                val missing = data.error == ApiFailureKind.NOT_FOUND || data.error == ApiFailureKind.DELETED
                AsyncStatePanel(stringResource(if (missing) R.string.guide_unavailable else guideErrorText(data.error)),
                    if (missing) R.drawable.ic_guide else R.drawable.ic_alert, Modifier.fillMaxWidth(),
                    description = when {
                        missing -> stringResource(R.string.guide_unavailable_hint)
                        data.error == ApiFailureKind.RATE_LIMITED -> null
                        else -> loadFailureHint(data.error)
                    }) {
                    ActionButton(stringResource(if (missing) R.string.guide_back_list else R.string.retry),
                        if (missing) onList else onRetry, Modifier.fillMaxWidth())
                }
            }
            else -> {
                InsetGroupCard(contentPadding = true) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryIconBadge(article.category, article.category.iconResource, sizeDp = 50)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(999.dp)) {
                                Text(stringResource(article.method.labelResource), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                            }
                            Text(stringResource(article.category.labelResource), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(article.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text(article.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GroupSectionLabel(stringResource(R.string.guide_steps))
                InsetGroupCard {
                    article.steps.forEachIndexed { index, step ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(8.dp)) {
                                Text(WasteFormat.integer(index.toLong() + 1), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                            }
                            Text(step, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (index < article.steps.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                if (article.cautions.isNotEmpty()) {
                    GroupSectionLabel(stringResource(R.string.guide_cautions))
                    InsetGroupCard(contentPadding = true) {
                        article.cautions.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                val review = article.review
                if (review == null) {
                    var showEditorial by rememberSaveable(article.id) { mutableStateOf(false) }
                    val editorialState = stringResource(if (showEditorial) R.string.daily_expanded else R.string.daily_collapsed)
                    InsetGroupCard {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(role = Role.Button) { showEditorial = !showEditorial }
                                .semantics { stateDescription = editorialState }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = RoundedCornerShape(999.dp)) {
                                Text(stringResource(R.string.guide_editorial_draft), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                            }
                            Text(
                                stringResource(R.string.editorial_details_toggle),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                painterResource(R.drawable.ic_chevron_down), null,
                                Modifier.size(20.dp).rotate(if (showEditorial) 180f else 0f),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (showEditorial) {
                            Text(
                                stringResource(R.string.guide_review_pending),
                                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    GroupSectionLabel(stringResource(R.string.guide_sources))
                    InsetGroupCard(contentPadding = true) {
                        TextActionButton(review.source.host, onClick = { onSource(review.source.toASCIIString()) })
                        Text(stringResource(R.string.guide_reviewed, WasteFormat.activityDate(review.date)), style = MaterialTheme.typography.bodySmall)
                        review.reviewer?.let { Text(stringResource(R.string.guide_reviewer, it), style = MaterialTheme.typography.bodySmall) }
                    }
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

@androidx.annotation.StringRes
private fun guideErrorText(error: ApiFailureKind?): Int =
    if (error == ApiFailureKind.RATE_LIMITED) R.string.error_rate_limited else R.string.guide_error
