package com.example.wastenot.ui.main

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.example.wastenot.*
import com.example.wastenot.R
import com.example.wastenot.data.demo.DemoActivityStore
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.data.repository.RemoteActivityStore
import com.example.wastenot.model.User
import com.example.wastenot.theme.Accent
import com.example.wastenot.theme.OnAccent
import com.example.wastenot.theme.ThemeMode
import java.time.LocalDate
import java.util.UUID

enum class MainTab(val key: NavKey, val label: Int, val icon: Int) {
    HOME(Home, R.string.tab_home, R.drawable.ic_home),
    HISTORY(History, R.string.tab_history, R.drawable.ic_history),
    GUIDE(Guide, R.string.tab_guide, R.drawable.ic_guide),
    STATISTICS(Statistics, R.string.tab_statistics, R.drawable.ic_statistics),
    PROFILE(Profile, R.string.tab_profile, R.drawable.ic_profile),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountNavigation(
    user: User,
    demo: Boolean,
    core: CoreRepository,
    theme: ThemeMode,
    onTheme: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(user.id, demo, core) { viewModelFactory { initializer {
        WorkspaceViewModel(if (demo) DemoActivityStore(user.timezone) else RemoteActivityStore(core))
    } } }
    val model: WorkspaceViewModel = viewModel(factory = factory)
    val data by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.load() }
    var selected by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val stacks = MainTab.entries.associateWith { rememberNavBackStack(it.key) }
    val stack = stacks.getValue(selected)
    val current = stack.last()
    val editor = current as? LogEditor
    val initial = data.records.find { it.id == editor?.logId }
    val date = model.referenceDate ?: LocalDate.now(user.timezone)
    val draft = if (editor != null && (editor.logId == null || initial != null)) {
        rememberSaveable(editor.requestId, saver = EditorDraft.Saver) { EditorDraft.from(initial, date) }
    } else null
    var discard by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    fun back() {
        if (editor != null && data.saving) return
        if (editor != null && draft?.dirty == true) { discard = true; return }
        if (stack.size > 1) stack.removeLastOrNull() else if (selected != MainTab.HOME) selected = MainTab.HOME
    }
    fun edit(id: String? = null) {
        if (stack.lastOrNull() !is LogEditor) stack.add(LogEditor(id, UUID.randomUUID().toString()))
    }
    LaunchedEffect(data.save) {
        val saved = data.save
        if (saved?.id != null && editor?.requestId == saved.requestId && stack.lastOrNull() == editor) {
            stack.removeLastOrNull()
            val detail = LogDetail(saved.id)
            if (stack.lastOrNull() != detail) stack.add(detail)
            model.acknowledgeSave(saved.requestId)
        }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_message)) },
        confirmButton = { TextButton(onClick = { discard = false; stack.removeLastOrNull() }) { Text(stringResource(R.string.discard_action)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.keep_editing)) } },
    )
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false }, title = { Text(stringResource(R.string.logout_title)) },
        text = { Text(stringResource(if (demo) R.string.logout_demo_message else R.string.logout_message)) },
        confirmButton = { TextButton(onClick = { leaving = false; onLogout() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.logout_action)) } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text(stringResource(R.string.cancel)) } },
    )
    BoxWithConstraints(modifier) {
        val rail = maxWidth >= 600.dp
        val railWidth = if (LocalDensity.current.fontScale >= 1.5f) 144.dp else 112.dp
        Row(Modifier.fillMaxSize()) {
            if (rail && editor == null) TabRail(selected, { selected = it }, { edit() }, Modifier.width(railWidth).fillMaxHeight())
            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                topBar = { TopAppBar(title = { Text(stringResource(when (current) {
                    is LogDetail -> R.string.log_detail
                    is LogEditor -> if (current.logId == null) R.string.log_create else R.string.log_edit
                    else -> selected.label
                }), style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
                    if (stack.size > 1) IconButton(onClick = ::back) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
                }) },
                bottomBar = { if (!rail && editor == null) TabDock(selected, { selected = it }, { edit() }) },
            ) { padding ->
                val pageInsets = PaddingValues(0.dp)
                val provider = entryProvider {
                    entry<Home> { OverviewScreen(user, data, demo, date, pageInsets, { edit() }, { selected = MainTab.HISTORY }, { stack.add(LogDetail(it)) }, { model.load(true) }, Modifier.fillMaxSize()) }
                    entry<History> { HistoryScreen(data, demo, pageInsets, { edit() }, { stack.add(LogDetail(it)) }, { model.load(true) }, Modifier.fillMaxSize()) }
                    entry<Guide> { GuidePlaceholderScreen(demo, pageInsets, Modifier.fillMaxSize()) }
                    entry<Statistics> { StatisticsScreen(data, demo, date, pageInsets, { model.load(true) }, Modifier.fillMaxSize()) }
                    entry<Profile> { ProfileScreen(user, data, demo, theme, pageInsets, onTheme, { leaving = true }, Modifier.fillMaxSize()) }
                    entry<LogDetail> { key -> LogDetailScreen(data.records.find { it.id == key.id }, demo, pageInsets, { edit(key.id) }, Modifier.fillMaxSize()) }
                    entry<LogEditor> { key ->
                        if (draft != null && editor == key) LogEditorScreen(draft, date, demo, data.saving,
                            data.save?.requestId == key.requestId && data.save?.error != null, pageInsets,
                            { model.save(it, key.logId, draft.values.version, key.requestId) }, Modifier.fillMaxSize())
                        else WorkspaceLoading(data, { model.load(true) }, pageInsets, Modifier.fillMaxSize())
                    }
                }
                val decorated = stacks.mapValues { (_, value) ->
                    rememberDecoratedNavEntries(value, listOf(rememberSaveableStateHolderNavEntryDecorator()), provider)
                }
                val entries = if (selected == MainTab.HOME) decorated.getValue(MainTab.HOME)
                    else decorated.getValue(MainTab.HOME) + decorated.getValue(selected)
                NavDisplay(entries = entries, onBack = ::back, modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding))
            }
        }
    }
}

@Composable
private fun RecordAction(onClick: () -> Unit, modifier: Modifier = Modifier, vertical: Boolean = false) {
    Button(onClick, modifier.heightIn(min = 52.dp),
        contentPadding = if (vertical) PaddingValues(horizontal = 8.dp, vertical = 12.dp) else ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)) {
        if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(R.drawable.ic_add), null)
            Text(stringResource(R.string.record_action), textAlign = TextAlign.Center)
        } else {
            Icon(painterResource(R.drawable.ic_add), null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.record_action))
        }
    }
}

@Composable
private fun TabDock(selected: MainTab, onSelect: (MainTab) -> Unit, onRecord: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column(Modifier.navigationBarsPadding().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RecordAction(onRecord)
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                MainTab.entries.forEach { tab -> TabItem(tab, tab == selected, { onSelect(tab) }, Modifier.weight(1f)) }
            }
        }
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
            .heightIn(min = 80.dp)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(tab.icon), null, Modifier.background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, MaterialTheme.shapes.medium).padding(8.dp),
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
