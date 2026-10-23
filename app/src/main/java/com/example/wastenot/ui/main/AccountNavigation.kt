package com.example.wastenot.ui.main

import androidx.compose.foundation.background
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.example.wastenot.*
import com.example.wastenot.R
import com.example.wastenot.data.dummy.dummyProgress
import com.example.wastenot.data.dummy.DummyActivityStore
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.data.repository.RemoteActivityStore
import com.example.wastenot.data.repository.GuideStore
import com.example.wastenot.model.User
import com.example.wastenot.theme.OnAccent
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.LocalWasteNotDockEdge
import java.time.LocalDate
import java.util.UUID

enum class MainTab(val key: NavKey, val label: Int, val icon: Int) {
    HOME(Home, R.string.tab_home, R.drawable.ic_home),
    HISTORY(History, R.string.tab_history, R.drawable.ic_history),
    GUIDE(Guide, R.string.tab_guide, R.drawable.ic_guide),
    STATISTICS(Statistics, R.string.tab_statistics, R.drawable.ic_statistics),
    PROFILE(Profile, R.string.tab_profile, R.drawable.ic_profile),
}

private data class EditorEntryDraft(val requestId: String, val draft: EditorDraft)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountNavigation(
    user: User,
    demo: Boolean,
    core: CoreRepository,
    guideStore: GuideStore,
    theme: ThemeMode,
    onTheme: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val factory = remember(user.id, demo, core) { viewModelFactory { initializer {
        WorkspaceViewModel(if (demo) DummyActivityStore(
            context.getSharedPreferences("dummy_logs_${user.id}", android.content.Context.MODE_PRIVATE), user.timezone,
            seedInitialRecords = user.id == "dummy-owner",
        ) else RemoteActivityStore(core))
    } } }
    val model: WorkspaceViewModel = viewModel(factory = factory)
    val data by model.state.collectAsStateWithLifecycle()
    val guideFactory = remember(guideStore) { viewModelFactory { initializer {
        GuideViewModel(guideStore, createSavedStateHandle())
    } } }
    val guides: GuideViewModel = viewModel(factory = guideFactory)
    val guideData by guides.state.collectAsStateWithLifecycle()
    val date = model.referenceDate ?: LocalDate.now(user.timezone)
    val profileProgress = remember(data.records, data.loading, data.error, demo, user.progress, date) {
        if (!demo) user.progress
        else if (data.records.isEmpty() && (data.loading || data.error != null)) null
        else dummyProgress(data.records, date)
    }
    val uriHandler = LocalUriHandler.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(model) { model.load() }
    var selected by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val stacks = MainTab.entries.associateWith { rememberNavBackStack(it.key) }
    val stack = stacks.getValue(selected)
    val current = stack.last()
    val editor = current as? LogEditor
    var activeEditorDraft by remember { mutableStateOf<EditorEntryDraft?>(null) }
    val draft = activeEditorDraft?.takeIf { it.requestId == editor?.requestId }?.draft
    var discard by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var capture by rememberSaveable { mutableStateOf<String?>(null) }
    var conflictRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var historyFilterOpen by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteVersion by rememberSaveable { mutableIntStateOf(0) }
    var deletedNotice by rememberSaveable { mutableStateOf(false) }
    var savedNoticeId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedWasCreated by rememberSaveable { mutableStateOf(false) }
    fun selectTab(tab: MainTab) { focus.clearFocus(); keyboard?.hide(); selected = tab }
    fun guideList() {
        val guideStack = stacks.getValue(MainTab.GUIDE)
        while (guideStack.size > 1) guideStack.removeLastOrNull()
        selectTab(MainTab.GUIDE)
    }
    fun history() {
        savedNoticeId = null
        if (stack.lastOrNull() is LogDetail) stack.removeLastOrNull()
        val historyStack = stacks.getValue(MainTab.HISTORY)
        while (historyStack.lastOrNull() is LogDetail) historyStack.removeLastOrNull()
        selected = MainTab.HISTORY
    }
    fun home() {
        savedNoticeId = null
        if (stack.lastOrNull() is LogDetail) stack.removeLastOrNull()
        val homeStack = stacks.getValue(MainTab.HOME)
        while (homeStack.lastOrNull() is LogDetail) homeStack.removeLastOrNull()
        selected = MainTab.HOME
    }
    fun back() {
        if (data.deletingId != null || (editor != null && data.saving)) return
        if (editor != null && (draft?.dirty == true || draft?.locked == true)) { discard = true; return }
        if (current is LogDetail && current.id == savedNoticeId) savedNoticeId = null
        if (stack.size > 1) stack.removeLastOrNull() else if (selected != MainTab.HOME) selected = MainTab.HOME
    }
    val latestBack by rememberUpdatedState({ back() })
    fun detail(id: String) {
        if ((stack.lastOrNull() as? LogDetail)?.id != id) stack.add(LogDetail(id))
    }
    val visibleDetail = current as? LogDetail
    LaunchedEffect(visibleDetail) { visibleDetail?.let { model.loadDetail(it.id) } }
    fun edit(id: String? = null) {
        if (stack.lastOrNull() !is LogEditor) stack.add(LogEditor(id, UUID.randomUUID().toString()))
    }
    LaunchedEffect(data.save, editor?.requestId, draft) {
        val saved = data.save
        if (saved?.id != null && editor?.requestId == saved.requestId && stack.lastOrNull() == editor) {
            savedNoticeId = saved.id
            savedWasCreated = saved.created
            stack.removeLastOrNull()
            if ((stack.lastOrNull() as? LogDetail)?.id != saved.id) stack.add(LogDetail(saved.id))
            model.acknowledgeSave(saved.requestId)
        } else if (saved?.error != null && editor?.requestId == saved.requestId && draft != null) {
            draft.submissionFailed(saved.error)
            if (draft.needsConflictResolution) conflictRequest = saved.requestId
            model.acknowledgeSave(saved.requestId)
        }
    }
    LaunchedEffect(data.deletion) {
        val result = data.deletion
        if (result?.deleted == true) {
            deletingId = null
            deletedNotice = true
            history()
            model.acknowledgeDelete(result.id)
        }
    }
    deletingId?.let { id ->
        (data.detail?.takeIf { it.id == id }?.log ?: data.records.find { it.id == id })?.let { log ->
            fun dismissDelete() { deletingId = null; model.acknowledgeDelete(id) }
            DeleteLogDialog(log, data.deletingId == id, data.deletion?.takeIf { it.id == id }?.error,
                onDismiss = ::dismissDelete,
                onConfirm = { model.delete(id, deleteVersion) },
                onReload = { dismissDelete(); model.loadDetail(id) },
                onUnavailable = { dismissDelete(); history(); model.load(true) })
        }
    }
    val pendingOutcome = draft?.submissionError?.saveOutcomeUnknown() == true
    if (discard) AlertDialog(
        onDismissRequest = { discard = false }, title = { Text(stringResource(if (pendingOutcome) R.string.close_editor_title else R.string.discard_title)) },
        text = { Text(stringResource(if (pendingOutcome) R.string.discard_pending_message else R.string.discard_message)) },
        confirmButton = { TextButton(onClick = { discard = false; stack.removeLastOrNull() }) {
            Text(stringResource(if (pendingOutcome) R.string.close_editor else R.string.discard_action))
        } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.keep_editing)) } },
    )
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false }, title = { Text(stringResource(R.string.logout_title)) },
        text = { Text(stringResource(R.string.logout_message)) },
        confirmButton = { TextButton(onClick = { leaving = false; onLogout() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.logout_action)) } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text(stringResource(R.string.cancel)) } },
    )
    capture?.let { session -> CaptureSheet(user.id, demo, session, onDismiss = { capture = null }, onPrefill = { prefill ->
        capture = null
        if (stack.lastOrNull() !is LogEditor) stack.add(LogEditor(null, UUID.randomUUID().toString(),
            scanCategory = prefill.category?.name, scanMethod = prefill.method?.name))
    }) }
    BoxWithConstraints(modifier) {
        val rail = maxWidth >= 600.dp
        val railWidth = if (LocalDensity.current.fontScale >= 1.5f) 144.dp else 112.dp
        val pageInsets = PaddingValues(0.dp)
        val provider = entryProvider {
            entry<Home> { OverviewScreen(user, data, demo, date, pageInsets, { edit() }, ::history, ::detail, { model.load(true) }, Modifier.fillMaxSize(), onProfile = { selected = MainTab.PROFILE }) }
            entry<History> { HistoryScreen(data, demo, pageInsets, { edit() }, ::detail, { model.load(true) }, Modifier.fillMaxSize(),
                today = date, filterOpen = historyFilterOpen, onOpenFilter = { historyFilterOpen = true }, onDismissFilter = { historyFilterOpen = false },
                deletedNotice = deletedNotice, onDismissNotice = { deletedNotice = false }) }
            entry<Guide> {
                LaunchedEffect(guides) { guides.load() }
                GuideListScreen(guideData, pageInsets, guides::query, guides::category,
                    onSearch = { guides.search() }, onMore = { guides.search(more = true) },
                    onDetail = { if (selected == MainTab.GUIDE && stack.lastOrNull() == Guide) stack.add(GuideDetail(it)) }, modifier = Modifier.fillMaxSize())
            }
            entry<GuideDetail> { key ->
                LaunchedEffect(key.id) { guides.detail(key.id, retry = true) }
                var sourceError by rememberSaveable(key.id) { mutableStateOf(false) }
                GuideDetailScreen(guideData.detail?.takeIf { it.id == key.id }, pageInsets,
                    onRetry = { guides.detail(key.id, retry = true) },
                    onList = { if (selected == MainTab.GUIDE && stack.lastOrNull() == key) guideList() },
                    onRecord = { if (selected == MainTab.GUIDE && stack.lastOrNull() == key) stack.add(LogEditor(null, UUID.randomUUID().toString(), key.id)) },
                    onSource = { url ->
                        try { uriHandler.openUri(url); sourceError = false }
                        catch (_: IllegalArgumentException) { sourceError = true }
                    }, modifier = Modifier.fillMaxSize(), sourceUnavailable = sourceError)
            }
            entry<Statistics> { StatisticsScreen(data, demo, date, pageInsets, { model.load(true) }, { edit() }, Modifier.fillMaxSize()) }
            entry<Profile> { ProfileScreen(user, profileProgress, data.loading, data.error, pageInsets,
                { model.load(true) }, { edit() }, { if (stacks.getValue(MainTab.PROFILE).lastOrNull() == Profile) stacks.getValue(MainTab.PROFILE).add(Settings) },
                { leaving = true }, Modifier.fillMaxSize()) }
            entry<Settings> { SettingsScreen(user, theme, pageInsets, onTheme, Modifier.fillMaxSize()) }
            entry<LogDetail> { key ->
                val read = data.detail?.takeIf { it.id == key.id }
                val log = if (read != null) read.log else data.records.find { it.id == key.id }
                val missing = read?.error == ApiFailureKind.NOT_FOUND || read?.error == ApiFailureKind.DELETED
                if (current == key && savedNoticeId == key.id && log != null) SaveConfirmationScreen(log, savedWasCreated, pageInsets,
                    onView = { savedNoticeId = null }, onHome = ::home, modifier = Modifier.fillMaxSize())
                else LogDetailScreen(log, read == null || read.loading, read?.error != null && !missing, pageInsets,
                    onEdit = { edit(key.id) },
                    onDelete = { if (log != null && log.version > 0) { deletingId = log.id; deleteVersion = log.version } },
                    onHistory = ::history, onRetry = { model.loadDetail(key.id) }, modifier = Modifier.fillMaxSize())
            }
            entry<LogEditor> { key ->
                val initial = data.detail?.takeIf { it.id == key.logId }?.log ?: data.records.find { it.id == key.logId }
                val article = guideData.detail?.takeIf { it.id == key.guideId && !it.loading && it.error == null }?.article
                if (key.guideId != null) LaunchedEffect(key.guideId) { guides.detail(key.guideId) }
                val canStart = (key.logId == null || initial != null) && (key.guideId == null || article != null)
                var entryDraft by rememberSaveable(key.requestId, stateSaver = EditorDraft.NullableSaver) {
                    mutableStateOf(if (canStart) {
                        if (article != null) EditorDraft.forGuide(article, date) else EditorDraft.from(initial, date).applyScan(key)
                    } else null)
                }
                LaunchedEffect(key.requestId, initial, article, date) {
                    if (entryDraft == null && canStart) {
                        entryDraft = if (article != null) EditorDraft.forGuide(article, date) else EditorDraft.from(initial, date).applyScan(key)
                    }
                }
                val form = entryDraft
                if (form != null) {
                    DisposableEffect(key.requestId, form) {
                        activeEditorDraft = EditorEntryDraft(key.requestId, form)
                        onDispose {
                            if (activeEditorDraft?.requestId == key.requestId) activeEditorDraft = null
                            model.clearConflict(key.requestId)
                        }
                    }
                    if (conflictRequest == key.requestId && form.needsConflictResolution && key.logId != null) {
                        fun dismissConflict() { conflictRequest = null; model.clearConflict(key.requestId) }
                        EditConflictDialog(form.values, data.conflict?.takeIf { it.requestId == key.requestId },
                            onDismiss = ::dismissConflict,
                            onLoad = { model.loadConflict(key.requestId, key.logId, checkNotNull(form.original.version)) },
                            onUse = { latest, keepDraft -> if (form.resolveConflict(latest, keepDraft)) dismissConflict() })
                    }
                    val leavingAfterSave = current != key && form.locked && form.submissionError == null
                    LogEditorScreen(form, date, user.timezone.id, demo, data.saving || leavingAfterSave, pageInsets,
                        { model.save(it, key.logId, form.values.version, key.requestId) },
                        { stack.removeLastOrNull(); history(); model.load(true) },
                        { if (stack.lastOrNull() == key) conflictRequest = key.requestId }, Modifier.fillMaxSize())
                } else if (canStart) {
                    ScrollPage(pageInsets, Modifier.fillMaxSize()) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.editor_preparing))
                    }
                } else if (key.guideId != null) {
                    GuideDetailScreen(guideData.detail?.takeIf { it.id == key.guideId }, pageInsets,
                        onRetry = { guides.detail(key.guideId, retry = true) }, onList = { if (selected == MainTab.GUIDE && stack.lastOrNull() == key) guideList() },
                        onRecord = {}, onSource = {}, modifier = Modifier.fillMaxSize())
                } else {
                    WorkspaceLoading(data, { model.load(true) }, pageInsets, Modifier.fillMaxSize())
                }
            }
        }
        val decorated = stacks.mapValues { (tab, value) ->
            val framedProvider: (NavKey) -> NavEntry<NavKey> = { key ->
                val entry = provider(key)
                NavEntry(navEntry = entry) {
                    val read = data.detail?.takeIf { it.id == (key as? LogDetail)?.id }
                    val log = if (read != null) read.log else data.records.find { it.id == (key as? LogDetail)?.id }
                    AccountPage(key, tab, rail, railWidth,
                        confirmation = key is LogDetail && key.id == savedNoticeId && log != null,
                        onBack = { if (selected == tab && stacks.getValue(selected).lastOrNull() == key) latestBack() },
                        onSelect = ::selectTab, onRecord = {
                            if (selected == tab && stacks.getValue(selected).lastOrNull() == tab.key) capture = UUID.randomUUID().toString()
                        },
                        onFilter = { if (selected == tab && stacks.getValue(selected).lastOrNull() == History) historyFilterOpen = true }) {
                        entry.Content()
                    }
                }
            }
            rememberDecoratedNavEntries(value, listOf(rememberSaveableStateHolderNavEntryDecorator()), framedProvider)
        }
        val entries = if (selected == MainTab.HOME) decorated.getValue(MainTab.HOME)
            else decorated.getValue(MainTab.HOME) + decorated.getValue(selected)
        NavDisplay(entries = entries, onBack = ::back,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            popTransitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPage(
    route: NavKey,
    tab: MainTab,
    rail: Boolean,
    railWidth: androidx.compose.ui.unit.Dp,
    confirmation: Boolean,
    onBack: () -> Unit,
    onSelect: (MainTab) -> Unit,
    onRecord: () -> Unit,
    onFilter: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Each fading entry keeps its own chrome so the outgoing list cannot resize and clamp its scroll.
    Row(Modifier.fillMaxSize().background(LocalWasteNotGradients.current.canvas)) {
        if (rail && route !is LogEditor) TabRail(tab, onSelect, onRecord, Modifier.width(railWidth).fillMaxHeight())
        Scaffold(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            containerColor = Color.Transparent,
            topBar = { if (route != Home) TopAppBar(title = { Text(stringResource(when (route) {
                is LogDetail -> if (confirmation) R.string.save_confirm_title else R.string.log_detail
                is LogEditor -> if (route.logId == null) R.string.log_create else R.string.log_edit
                is GuideDetail -> R.string.guide_detail
                Settings -> R.string.settings_title
                else -> tab.label
            }), style = MaterialTheme.typography.headlineMedium) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent), navigationIcon = {
                if (route != tab.key && !confirmation) FilledIconButton(onClick = onBack, shape = MaterialTheme.shapes.small,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.padding(start = 16.dp).size(48.dp)) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
            }, actions = {
                if (route == History) FilledIconButton(onClick = onFilter, shape = MaterialTheme.shapes.medium,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.padding(end = 12.dp).size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_filter), stringResource(R.string.filter_history))
                }
            }) },
            bottomBar = { if (!rail && route == tab.key) TabDock(tab, onSelect, onRecord) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
        }
    }
}

@Composable
private fun TabDock(selected: MainTab, onSelect: (MainTab) -> Unit, onRecord: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.navigationBarsPadding().padding(start = 7.dp, end = 7.dp, bottom = 9.dp)) {
        val measurer = rememberTextMeasurer()
        val style = MaterialTheme.typography.labelMedium
        val labels = MainTab.entries.map { stringResource(it.label) }
        val widest = with(LocalDensity.current) {
            labels.maxOf { measurer.measure(it, style = style, softWrap = false).size.width }.toDp()
        }
        // Reflow whole labels when larger text needs more room than five columns provide.
        val columns = listOf(5, 3, 2, 1).firstOrNull {
            val cellWidth = (maxWidth - 4.dp) / it
            cellWidth >= 48.dp && widest + 8.dp <= cellWidth
        } ?: 1
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 27.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LocalWasteNotDockEdge.current),
            shadowElevation = 2.dp,
        ) {
            FlowRow(
                modifier = Modifier.padding(top = 32.dp, bottom = 7.dp, start = 2.dp, end = 2.dp).selectableGroup(),
                maxItemsInEachRow = columns,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MainTab.entries.forEach { tab -> TabItem(tab, tab == selected, { onSelect(tab) }, Modifier.weight(1f)) }
            }
        }
        RecordAction(onRecord, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun TabRail(selected: MainTab, onSelect: (MainTab) -> Unit, onRecord: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
                .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RecordAction(onRecord, Modifier.padding(horizontal = 4.dp), vertical = true)
            MainTab.entries.forEach { tab -> TabItem(tab, tab == selected, { onSelect(tab) }, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun TabItem(tab: MainTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(19.dp))
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(width = 46.dp, height = 28.dp).then(if (selected) Modifier.background(LocalWasteNotGradients.current.accent,
            androidx.compose.foundation.shape.RoundedCornerShape(9.dp)) else Modifier), contentAlignment = Alignment.Center) {
            Icon(painterResource(tab.icon), null, Modifier.size(20.dp), tint = if (selected) OnAccent else MaterialTheme.colorScheme.onSurface)
        }
        Text(stringResource(tab.label), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
