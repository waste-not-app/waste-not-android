package com.example.wastenot.ui.main

import com.example.wastenot.data.local.ScanPhotoCache
import com.example.wastenot.data.repository.ScanRepository
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.data.local.AttachmentImagePreparer
import com.example.wastenot.data.local.PrepareAttachmentResult
import com.example.wastenot.data.local.ReminderScheduler
import com.example.wastenot.data.local.ReminderSetting
import com.example.wastenot.data.local.ReminderStore
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.data.repository.AccountManagement
import com.example.wastenot.model.Metadata
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.example.wastenot.ui.components.ambientCanvasBackground
import com.example.wastenot.theme.LocalWasteNotScaffoldPadding
import com.example.wastenot.theme.LocalWasteNotSurfaceTokens
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.example.wastenot.*
import com.example.wastenot.R
import com.example.wastenot.data.local.LocalEditorDraftStore
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.repository.GuideStore
import com.example.wastenot.model.User
import com.example.wastenot.theme.OnAccent
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.LocalWasteNotDockEdge
import com.example.wastenot.ui.auth.LogoutUiState
import com.example.wastenot.ui.auth.AccountDeletionViewModel
import com.example.wastenot.ui.auth.AccountDeletionState
import com.example.wastenot.ui.auth.AccountDeletionScreen
import com.example.wastenot.ui.components.rememberPrivacyPolicyAction
import com.example.wastenot.ui.components.IconActionButton
import com.example.wastenot.ui.components.GlassChromeSurface
import com.example.wastenot.ui.components.focusOutline
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

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
    activityStore: ActivityStore,
    guideStore: GuideStore,
    scanRepositoryFactory: (ScanPhotoCache) -> ScanRepository,
    theme: ThemeMode,
    onTheme: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    logoutState: LogoutUiState = LogoutUiState.None,
    clock: Clock = Clock.systemUTC(),
    restorePhotoReview: Boolean = false,
    onPhotoReviewChanged: (Boolean) -> Unit = {},
    coreRepository: CoreRepository? = null,
    reminderStore: ReminderStore? = null,
    reminderScheduler: ReminderScheduler? = null,
    accountManagement: AccountManagement? = null,
) {
    val context = LocalContext.current.applicationContext
    val factory = remember(activityStore) { viewModelFactory { initializer {
        WorkspaceViewModel(activityStore)
    } } }
    val model: WorkspaceViewModel = viewModel(factory = factory)
    val data by model.state.collectAsStateWithLifecycle()
    val syncFactory = remember(activityStore) { viewModelFactory { initializer { ActivitySyncViewModel(activityStore) } } }
    val syncModel: ActivitySyncViewModel = viewModel(factory = syncFactory)
    val syncIssues by syncModel.issues.collectAsStateWithLifecycle()
    val historyReady by syncModel.historyReady.collectAsStateWithLifecycle()
    val syncBusy by syncModel.busy.collectAsStateWithLifecycle()
    val syncError by syncModel.error.collectAsStateWithLifecycle()
    val accountDeletionFactory = remember(accountManagement) {
        accountManagement?.let { accounts -> viewModelFactory { initializer { AccountDeletionViewModel(accounts) } } }
    }
    val accountDeletionModel: AccountDeletionViewModel? = accountDeletionFactory?.let { viewModel(factory = it) }
    val accountDeletionState = accountDeletionModel?.state?.collectAsStateWithLifecycle()?.value ?: remember { AccountDeletionState() }
    val openPrivacy = rememberPrivacyPolicyAction()
    var showingSync by rememberSaveable { mutableStateOf(false) }
    if (showingSync) ActivitySyncSheet(syncIssues, syncBusy, syncError, syncModel::retry, syncModel::resolve, { showingSync = false })
    val persistenceFactory = remember(user.id) { viewModelFactory { initializer {
        EditorPersistenceViewModel(LocalEditorDraftStore.create(context, user.id))
    } } }
    val persistence: EditorPersistenceViewModel = viewModel(factory = persistenceFactory)
    val disk by persistence.state.collectAsStateWithLifecycle()
    LaunchedEffect(persistence) { persistence.load() }
    var discardUnreadable by remember { mutableStateOf(false) }
    if (disk.loading || !disk.ready) {
        if (discardUnreadable) DiscardStoredDraftDialog(
            { discardUnreadable = false }, { persistence.clear { discardUnreadable = false } },
            busy = disk.busy, error = disk.failure == DraftFailure.FINISH,
        )
        DraftRestoreScreen(disk.loading, { persistence.load(retry = true) }, { discardUnreadable = true }, modifier)
        return
    }
    val recovered = remember(persistence) { disk.active }
    val guideFactory = remember(guideStore) { viewModelFactory { initializer {
        GuideViewModel(guideStore, createSavedStateHandle())
    } } }
    val guides: GuideViewModel = viewModel(factory = guideFactory)
    val guideData by guides.state.collectAsStateWithLifecycle()
    val historyFactory = remember(activityStore) { viewModelFactory { initializer {
        HistoryViewModel(activityStore, createSavedStateHandle())
    } } }
    val historyModel: HistoryViewModel = viewModel(factory = historyFactory)
    val historyData by historyModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(historyModel, data.confirmedChange) { data.confirmedChange?.let(historyModel::changed) }
    LaunchedEffect(historyModel, data.detail) {
        data.detail?.takeUnless { it.loading }?.let { read ->
            if (read.error == ApiFailureKind.NOT_FOUND || read.error == ApiFailureKind.DELETED) historyModel.unavailable(read.id)
            else read.log?.let { historyModel.reconcile(it) }
        }
    }
    val date by rememberAccountDate(user.timezone, model.referenceDate, clock)
    val summariesFactory = remember(activityStore) { viewModelFactory { initializer {
        SummariesViewModel(activityStore, createSavedStateHandle()) { model.referenceDate ?: LocalDate.now(clock.withZone(user.timezone)) }
    } } }
    val summaries: SummariesViewModel = viewModel(factory = summariesFactory)
    val summaryData by summaries.state.collectAsStateWithLifecycle()
    LaunchedEffect(summaries, data.mutationRevision, date) { summaries.refresh() }
    val uriHandler = LocalUriHandler.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(model) { model.load() }
    var selected by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var dismissedSyncKey by rememberSaveable { mutableStateOf<String?>(null) }
    var metadata by remember(coreRepository) { mutableStateOf<Metadata?>(null) }
    LaunchedEffect(coreRepository, selected, data.mutationRevision) {
        if (coreRepository != null && metadata == null) {
            val meta = coreRepository.metadata()
            if (meta is ApiResult.Success) metadata = meta.value
        }
    }
    val achievementsEnabled = metadata?.isEnabled("achievements") == true
    val photosEnabled = metadata?.isEnabled("photos") == true
    val achievementsFactory = remember(coreRepository, activityStore) {
        coreRepository?.let { repo -> viewModelFactory { initializer { AchievementsViewModel(repo, activityStore.synchronizationAcknowledgements) } } }
    }
    val achievementsModel: AchievementsViewModel? = achievementsFactory?.let { viewModel(factory = it) }
    val achievementsState = achievementsModel?.state?.collectAsStateWithLifecycle()?.value ?: AchievementsUiState()
    val reminderFlow = remember(reminderStore, user.id) {
        reminderStore?.observe(user.id) ?: flowOf(ReminderSetting())
    }
    val reminderSetting: ReminderSetting? by reminderFlow.collectAsStateWithLifecycle(initialValue = null)
    val reminderSaveModel: ReminderSaveViewModel = viewModel()
    val reminderSaveProgress by reminderSaveModel.progress.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var photoBytesCache by remember { mutableStateOf<Map<String, ByteArray>>(emptyMap()) }
    var detailPhotoLoadingId by remember { mutableStateOf<String?>(null) }
    var detailPhotoErrorId by remember { mutableStateOf<String?>(null) }
    var detailPhotoAttempt by remember { mutableIntStateOf(0) }
    var editorPhotoBusyRequest by remember { mutableStateOf<String?>(null) }
    var editorPhotoError by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val stacks = MainTab.entries.associateWith { rememberNavBackStack(it.key) }
    val stack = stacks.getValue(selected)
    val current = stack.last()
    val editor = current as? LogEditor
    var activeEditorDraft by remember { mutableStateOf<EditorEntryDraft?>(null) }
    val draft = activeEditorDraft?.takeIf { it.requestId == editor?.requestId }?.draft
    var discard by remember { mutableStateOf(false) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var capture by rememberSaveable { mutableStateOf(if (restorePhotoReview) UUID.randomUUID().toString() else null) }
    var conflictRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var historyFilterOpen by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteVersion by rememberSaveable { mutableIntStateOf(0) }
    var deletedNotice by rememberSaveable { mutableStateOf(false) }
    var savedNoticeId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedWasCreated by rememberSaveable { mutableStateOf(false) }
    var navigationReady by remember { mutableStateOf(false) }
    var resumingPrompt by remember { mutableStateOf(false) }
    fun resumeDraft() {
        val saved = persistence.resume() ?: return
        val tab = MainTab.valueOf(saved.origin)
        val target = stacks.getValue(tab)
        if (target.none { it is LogEditor && it.requestId == saved.requestId }) {
            saved.logId?.let { if ((target.lastOrNull() as? LogDetail)?.id != it) target.add(LogDetail(it)) }
            saved.guideId?.let { if (target.lastOrNull() != GuideDetail(it)) target.add(GuideDetail(it)) }
            target.add(saved.editorKey())
        }
        selected = tab
        resumingPrompt = false
    }
    LaunchedEffect(persistence) {
        stacks.values.forEach { savedStack ->
            savedStack.removeAll { it is LogEditor && it.requestId != recovered?.requestId }
        }
        if (recovered != null && !recovered.paused) resumeDraft()
        navigationReady = true
    }
    fun selectTab(tab: MainTab) {
        if (accountDeletionState.submitting) return
        if (current == DeleteAccount) accountDeletionModel?.dismiss()
        focus.clearFocus(); keyboard?.hide(); selected = tab
    }
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
        if (accountDeletionState.submitting) return
        if (current == DeleteAccount) accountDeletionModel?.dismiss()
        if (data.deletingId != null || disk.busy || (editor != null && data.saving)) return
        if (editor != null && draft == null && disk.active?.requestId == editor.requestId) return
        if (editor != null && data.save?.let { it.requestId == editor.requestId && it.id != null } == true) return
        if (editor != null && (draft?.dirty == true || draft?.locked == true)) { discard = true; return }
        if (editor != null && draft != null) {
            persistence.leave(draft.stored(editor, selected)) {
                if (stack.lastOrNull() == editor) stack.removeLastOrNull()
            }
            return
        }
        if (current is LogDetail && current.id == savedNoticeId) savedNoticeId = null
        if (stack.size > 1) stack.removeLastOrNull() else if (selected != MainTab.HOME) selected = MainTab.HOME
    }
    val latestBack by rememberUpdatedState({ back() })
    fun detail(id: String) {
        if ((stack.lastOrNull() as? LogDetail)?.id != id) stack.add(LogDetail(id))
    }
    val visibleDetail = current as? LogDetail
    LaunchedEffect(visibleDetail) { visibleDetail?.let { model.loadDetail(it.id) } }
    fun openEditor(key: LogEditor) {
        if (disk.busy) return
        if (disk.active?.paused == true) { resumingPrompt = true; return }
        if (stack.lastOrNull() !is LogEditor) stack.add(key)
    }
    fun edit(id: String? = null) { openEditor(LogEditor(id, UUID.randomUUID().toString())) }
    fun openCapture() {
        if (accountDeletionState.submitting) return
        if (disk.active?.paused == true) resumingPrompt = true
        else if (!disk.busy) capture = UUID.randomUUID().toString()
    }
    fun finishSave() {
        val saved = data.save ?: return
        val key = editor ?: return
        val form = draft ?: return
        if (saved.id == null || saved.requestId != key.requestId || stack.lastOrNull() != key) return
        persistence.leave(form.stored(key, selected)) {
            if (stack.lastOrNull() == key) {
                savedNoticeId = saved.id
                savedWasCreated = saved.created
                stack.removeLastOrNull()
                if ((stack.lastOrNull() as? LogDetail)?.id != saved.id) stack.add(LogDetail(saved.id))
                model.acknowledgeSave(saved.requestId)
            }
        }
    }
    LaunchedEffect(data.save, editor?.requestId, draft) {
        val saved = data.save
        if (saved?.id != null && editor?.requestId == saved.requestId && stack.lastOrNull() == editor) {
            finishSave()
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
                onUnavailable = { dismissDelete(); history(); historyModel.unavailable(id); model.load(true) })
        }
    }
    val pendingOutcome = draft?.outcomeUnknown == true
    if (discard) DiscardChangesDialog(
        pendingOutcome = pendingOutcome,
        onDismiss = { discard = false },
        onConfirm = {
            if (draft != null && editor != null) persistence.leave(draft.stored(editor, selected), keepForReview = pendingOutcome) {
                discard = false
                if (stack.lastOrNull() == editor) stack.removeLastOrNull()
            }
        },
        busy = disk.busy, error = disk.failure == DraftFailure.FINISH,
    )
    if (leaving) LogoutDialog(
        onDismiss = { leaving = false },
        onConfirm = { persistence.clear { onLogout() } },
        busy = disk.busy, error = disk.failure == DraftFailure.FINISH, hasDraft = disk.active != null,
        hasPendingSync = syncIssues.isNotEmpty(), sessionPending = logoutState is LogoutUiState.Pending,
        sessionFailed = (logoutState as? LogoutUiState.Complete)?.result?.localCleared == false,
    )
    if (resumingPrompt) ResumeDraftDialog({ resumingPrompt = false }, ::resumeDraft)
    capture?.let { session -> CaptureSheet(user.id, scanRepositoryFactory, session, activityStore::isPhotoScanDisabled,
        onDismiss = { capture = null }, onPrefill = { prefill ->
        capture = null
        openEditor(LogEditor(null, UUID.randomUUID().toString(), scanCategory = prefill.category?.name,
            scanMethod = prefill.method?.name, scanRecommendationId = prefill.aiProvenance?.recommendationId))
    }, restorePhotoReview = restorePhotoReview, onPhotoReviewChanged = onPhotoReviewChanged) }
    BoxWithConstraints(modifier) {
        val rail = maxWidth >= 600.dp
        val railWidth = if (LocalDensity.current.fontScale >= 1.5f) 144.dp else 112.dp
        val pageInsets = PaddingValues(0.dp)
        val provider = entryProvider {
            entry<Home> { OverviewScreen(user, summaryData.dashboard, pageInsets, { edit() }, ::history, ::detail, { summaries.dashboard(true) }, Modifier.fillMaxSize(),
                onProfile = { selected = MainTab.PROFILE }, onResumeDraft = if (disk.active?.paused == true) ::resumeDraft else null) }
            entry<History> {
                LaunchedEffect(historyModel) { historyModel.load() }
                HistoryScreen(historyData, pageInsets, { edit() }, ::detail, historyModel::refresh,
                historyModel::filter, historyModel::more, Modifier.fillMaxSize(),
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
                    onRecord = { if (selected == MainTab.GUIDE && stack.lastOrNull() == key) openEditor(LogEditor(null, UUID.randomUUID().toString(), key.id)) },
                    onSource = { url ->
                        try { uriHandler.openUri(url); sourceError = false }
                        catch (_: IllegalArgumentException) { sourceError = true }
                        catch (_: SecurityException) { sourceError = true }
                    }, modifier = Modifier.fillMaxSize(), sourceUnavailable = sourceError)
            }
            entry<Statistics> {
                LaunchedEffect(summaries) { summaries.statistics() }
                StatisticsScreen(summaryData.statistics, summaryData.days, pageInsets, { summaries.statistics(retry = true) }, { edit() },
                    onDays = { summaries.statistics(it) }, modifier = Modifier.fillMaxSize())
            }
            entry<Profile> { ProfileScreen(user, summaryData.dashboard.value?.progress, summaryData.dashboard.loading, summaryData.dashboard.error, pageInsets,
                { summaries.dashboard(true) }, { edit() }, { if (stacks.getValue(MainTab.PROFILE).lastOrNull() == Profile) stacks.getValue(MainTab.PROFILE).add(Settings) },
                { leaving = true }, Modifier.fillMaxSize(),
                achievementsEnabled = achievementsEnabled,
                onAchievements = { if (stacks.getValue(MainTab.PROFILE).lastOrNull() == Profile) stacks.getValue(MainTab.PROFILE).add(Achievements) },
                onSync = if (activityStore is com.example.wastenot.data.repository.OfflineActivityStore) ({ showingSync = true }) else null,
                onRecyclingPoints = { if (stacks.getValue(MainTab.PROFILE).lastOrNull() == Profile) stacks.getValue(MainTab.PROFILE).add(RecyclingPointList) },
                freshness = summaryData.dashboard.freshness, stale = summaryData.dashboard.stale) }
            entry<Achievements> {
                LaunchedEffect(achievementsModel) { achievementsModel?.load() }
                AchievementsScreen(
                    state = achievementsState,
                    accountTimezone = user.timezone,
                    padding = pageInsets,
                    onRetry = { achievementsModel?.refresh() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<Settings> {
                SettingsScreen(
                    user = user,
                    theme = theme,
                    padding = pageInsets,
                    onTheme = onTheme,
                    modifier = Modifier.fillMaxSize(),
                    onReminderSettings = { if (stacks.getValue(MainTab.PROFILE).lastOrNull() == Settings) stacks.getValue(MainTab.PROFILE).add(ReminderSettings) },
                    onDeleteAccount = accountDeletionModel?.let { deletion -> ({
                        deletion.dismiss()
                        if (stacks.getValue(MainTab.PROFILE).lastOrNull() == Settings) stacks.getValue(MainTab.PROFILE).add(DeleteAccount)
                    }) },
                )
            }
            entry<DeleteAccount> {
                if (accountDeletionModel != null) AccountDeletionScreen(
                    state = accountDeletionState, padding = pageInsets,
                    onPassword = accountDeletionModel::password, onAcknowledge = accountDeletionModel::acknowledge,
                    onDelete = accountDeletionModel::delete, onPrivacy = openPrivacy, onBack = ::back,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<RecyclingPointList> {
                RecyclingPointListScreen(pageInsets, onDetail = { id ->
                    if (stack.lastOrNull() == RecyclingPointList) stack.add(RecyclingPointDetail(id))
                }, modifier = Modifier.fillMaxSize())
            }
            entry<RecyclingPointDetail> { key ->
                RecyclingPointDetailScreen(key.pointId, pageInsets, onList = ::back, modifier = Modifier.fillMaxSize())
            }
            entry<ReminderSettings> {
                ReminderSettingsScreen(
                    setting = reminderSetting,
                    padding = pageInsets,
                    onSave = { next -> reminderSaveModel.save(user.id, next, user.timezone, reminderStore, reminderScheduler) },
                    accountSave = reminderSaveProgress,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<LogDetail> { key ->
                val read = data.detail?.takeIf { it.id == key.id }
                val log = if (read != null) read.log else data.records.find { it.id == key.id }
                val missing = read?.error == ApiFailureKind.NOT_FOUND || read?.error == ApiFailureKind.DELETED
                val photoId = log?.photoId
                LaunchedEffect(photosEnabled, photoId, detailPhotoAttempt) {
                    if (photosEnabled && photoId != null && photoId !in photoBytesCache && coreRepository != null) {
                        detailPhotoLoadingId = photoId
                        detailPhotoErrorId = null
                        when (val res = coreRepository.downloadPhoto(photoId)) {
                            is ApiResult.Success -> photoBytesCache = photoBytesCache + (photoId to res.value)
                            is ApiResult.Failure -> detailPhotoErrorId = photoId
                        }
                        if (detailPhotoLoadingId == photoId) detailPhotoLoadingId = null
                    }
                }
                if (current == key && savedNoticeId == key.id && log != null) SaveConfirmationScreen(log, savedWasCreated, pageInsets,
                    onView = { savedNoticeId = null }, onHome = ::home, modifier = Modifier.fillMaxSize(),
                    wasQueued = activityStore is com.example.wastenot.data.repository.OfflineActivityStore)
                else LogDetailScreen(log, read == null || read.loading, read?.error != null && !missing, pageInsets,
                    onEdit = { edit(key.id) },
                    onDelete = { if (log != null && log.version > 0) { deletingId = log.id; deleteVersion = log.version } },
                    onHistory = ::history, onRetry = { model.loadDetail(key.id) }, modifier = Modifier.fillMaxSize(),
                    photosEnabled = photosEnabled,
                    photoBytes = photoId?.let { photoBytesCache[it] },
                    photoLoading = photoId != null && detailPhotoLoadingId == photoId,
                    photoError = photoId != null && detailPhotoErrorId == photoId,
                    onRetryPhoto = { detailPhotoAttempt++ }) }
            entry<LogEditor> { key ->
                val cached = disk.active?.takeIf { it.requestId == key.requestId }
                val initial = data.detail?.takeIf { it.id == key.logId }?.log ?: data.records.find { it.id == key.logId }
                val article = guideData.detail?.takeIf { it.id == key.guideId && !it.loading && it.error == null }?.article
                if (key.guideId != null) LaunchedEffect(key.guideId) { guides.detail(key.guideId) }
                val canStart = cached != null || ((key.logId == null || initial != null) && (key.guideId == null || article != null))
                val wasRestored = rememberSaveable(key.requestId) { cached != null }
                var entryDraft by rememberSaveable(key.requestId, stateSaver = EditorDraft.NullableSaver) {
                    mutableStateOf(if (canStart) {
                        cached?.restoreForm() ?: if (article != null) EditorDraft.forGuide(article, date) else EditorDraft.from(initial, date).applyScan(key)
                    } else null)
                }
                LaunchedEffect(key.requestId, initial, article, date) {
                    if (entryDraft == null && canStart) {
                        entryDraft = cached?.restoreForm() ?: if (article != null) EditorDraft.forGuide(article, date) else EditorDraft.from(initial, date).applyScan(key)
                    }
                }
                val form = entryDraft
                if (form != null) {
                    LaunchedEffect(key.requestId, form, current == key) {
                        if (current == key) snapshotFlow { form.stored(key, selected) }.collect(persistence::update)
                    }
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
                    val confirmed = data.save?.let { it.requestId == key.requestId && it.id != null } == true
                    val editorPhotoId = form.values.photoId
                    LaunchedEffect(photosEnabled, editorPhotoId) {
                        if (photosEnabled && editorPhotoId != null && editorPhotoId !in photoBytesCache && coreRepository != null) {
                            val res = coreRepository.downloadPhoto(editorPhotoId)
                            if (res is ApiResult.Success) photoBytesCache = photoBytesCache + (editorPhotoId to res.value)
                        }
                    }
                    LogEditorScreen(form, date, user.timezone.id, data.saving || disk.busy || leavingAfterSave, pageInsets,
                        { input -> persistence.checkpoint(form.stored(key, selected),
                            { if (stack.lastOrNull() == key) model.save(input, key.logId, form.values.version, key.requestId,
                                previousOutcomeUnknown = form.outcomeUnknown) },
                            { form.submissionFailed(ApiFailureKind.STORAGE) }) },
                        { persistence.leave(form.stored(key, selected)) {
                            if (stack.lastOrNull() == key) { stack.removeLastOrNull(); history(); model.load(true) }
                        } },
                        { if (stack.lastOrNull() == key) conflictRequest = key.requestId }, Modifier.fillMaxSize(),
                        persistence = disk, restored = wasRestored, saveConfirmed = confirmed,
                        scanPrefilled = key.scanCategory != null && key.scanMethod != null,
                        onRetryPersistence = { if (confirmed) finishSave() else persistence.update(form.stored(key, selected)) },
                        photosEnabled = photosEnabled,
                        photoPreviewBytes = editorPhotoId?.let { photoBytesCache[it] },
                        photoBusy = editorPhotoBusyRequest == key.requestId,
                        photoError = editorPhotoError?.takeIf { it.first == key.requestId }?.second,
                        onPickPhoto = { uri ->
                            if (coreRepository != null && editorPhotoBusyRequest == null) {
                                editorPhotoBusyRequest = key.requestId
                                editorPhotoError = null
                                coroutineScope.launch {
                                    try {
                                        when (val prep = AttachmentImagePreparer.prepare(context, uri)) {
                                            is PrepareAttachmentResult.UnsupportedFormat ->
                                                editorPhotoError = key.requestId to R.string.photo_error_format
                                            PrepareAttachmentResult.TooLarge, PrepareAttachmentResult.Unreadable ->
                                                editorPhotoError = key.requestId to R.string.photo_error_too_large
                                            is PrepareAttachmentResult.Success -> {
                                                when (val uploaded = coreRepository.uploadPhoto(prep.photo.mimeType, prep.photo.bytes, prep.photo.fileName)) {
                                                    is ApiResult.Success -> {
                                                        val prevStaged = form.values.photoId?.takeIf { it != form.original.photoId }
                                                        photoBytesCache = photoBytesCache + (uploaded.value.id to prep.photo.bytes)
                                                        form.attachPhoto(uploaded.value.id)
                                                        if (prevStaged != null) {
                                                            coreRepository.deletePhoto(prevStaged)
                                                        }
                                                    }
                                                    is ApiResult.Failure ->
                                                        editorPhotoError = key.requestId to R.string.photo_error_upload
                                                }
                                            }
                                        }
                                    } finally {
                                        if (editorPhotoBusyRequest == key.requestId) editorPhotoBusyRequest = null
                                    }
                                }
                            }
                        },
                        onRemovePhoto = {
                            val prevStaged = form.values.photoId?.takeIf { it != form.original.photoId }
                            form.removePhoto()
                            editorPhotoError = null
                            if (prevStaged != null && coreRepository != null) {
                                coroutineScope.launch { coreRepository.deletePhoto(prevStaged) }
                            }
                        },
                        onDismissPhotoError = { editorPhotoError = null })
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
                            if (selected == tab && stacks.getValue(selected).lastOrNull() == key) openCapture()
                        },
                        onFilter = { if (selected == tab && stacks.getValue(selected).lastOrNull() == History) historyFilterOpen = true }) {
                        Box(Modifier.fillMaxSize()) {
                            entry.Content()
                            if (activityStore is com.example.wastenot.data.repository.OfflineActivityStore) {
                                val syncToastTopPadding = if (key is LogEditor) 8.dp
                                else LocalWasteNotScaffoldPadding.current.calculateTopPadding() + 8.dp
                                ActivitySyncToast(
                                    count = syncIssues.size,
                                    ready = historyReady,
                                    dismissedKey = dismissedSyncKey,
                                    onDismissedKeyChange = { dismissedSyncKey = it },
                                    onOpen = { showingSync = true },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = syncToastTopPadding),
                                )
                            }
                        }
                    }
                }
            }
            rememberDecoratedNavEntries(value, listOf(rememberSaveableStateHolderNavEntryDecorator()), framedProvider)
        }
        val entries = if (selected == MainTab.HOME) decorated.getValue(MainTab.HOME)
            else decorated.getValue(MainTab.HOME) + decorated.getValue(selected)
        if (navigationReady) NavDisplay(entries = entries, onBack = ::back,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            popTransitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.fillMaxSize())
        else DraftRestoreScreen(true, {}, {}, Modifier.fillMaxSize().background(LocalWasteNotGradients.current.canvas))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountPage(
    route: NavKey,
    tab: MainTab,
    rail: Boolean,
    railWidth: androidx.compose.ui.unit.Dp,
    confirmation: Boolean,
    onBack: () -> Unit,
    onSelect: (MainTab) -> Unit,
    onRecord: () -> Unit,
    onFilter: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hazeState = rememberHazeState()
    val tokens = LocalWasteNotSurfaceTokens.current
    val colors = MaterialTheme.colorScheme
    val glassStyle = remember(tokens, colors.surface, colors.background) {
        HazeStyle(
            backgroundColor = colors.surface,
            tint = HazeTint(tokens.glassTintStrong),
            blurRadius = 24.dp,
            noiseFactor = 0.035f,
            fallbackTint = HazeTint(tokens.glassTintStrong.compositeOver(colors.background)),
        )
    }
    val headerShape = MaterialTheme.shapes.large
    // Each fading entry owns its chrome so an outgoing list keeps its scroll constraints.
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().ambientCanvasBackground().hazeSource(hazeState, zIndex = 0f))
        Row(Modifier.fillMaxSize()) {
            if (rail && route !is LogEditor) TabRail(tab, onSelect, onRecord, Modifier.width(railWidth).fillMaxHeight())
            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                containerColor = Color.Transparent,
                topBar = {
                    if (route != Home) {
                        GlassChromeSurface(
                            hazeState = hazeState,
                            style = glassStyle,
                            shape = headerShape,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (route != tab.key && !confirmation) {
                                    IconActionButton(R.drawable.ic_back, stringResource(R.string.back), onBack)
                                }
                                Text(
                                    stringResource(
                                        when (route) {
                                            is LogDetail -> if (confirmation) R.string.save_confirm_title else R.string.log_detail
                                            is LogEditor -> if (route.logId == null) R.string.log_create else R.string.log_edit
                                            is GuideDetail -> R.string.guide_detail
                                            Settings -> R.string.settings_title
                                            Achievements -> R.string.achievements_title
                                            ReminderSettings -> R.string.reminder_title
                                            DeleteAccount -> R.string.delete_account_title
                                            RecyclingPointList, is RecyclingPointDetail -> R.string.recycling_points_open
                                            else -> tab.label
                                        },
                                    ),
                                    modifier = Modifier.weight(1f).semantics { heading() },
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (route == History) {
                                    IconActionButton(R.drawable.ic_filter, stringResource(R.string.filter_history), onFilter)
                                }
                            }
                        }
                    }
                },
                bottomBar = { if (!rail && route == tab.key) TabDock(tab, onSelect, onRecord, hazeState, glassStyle) },
            ) { padding ->
                CompositionLocalProvider(LocalWasteNotScaffoldPadding provides padding) {
                    val bodyModifier = if (route is LogEditor) {
                        Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).hazeSource(hazeState, zIndex = 1f)
                    } else {
                        Modifier.fillMaxSize().hazeSource(hazeState, zIndex = 1f)
                    }
                    Box(bodyModifier) { content() }
                }
            }
        }
    }
}

@Composable
private fun TabDock(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    onRecord: () -> Unit,
    hazeState: HazeState,
    glassStyle: HazeStyle,
    modifier: Modifier = Modifier,
) {
    val dockShape = MaterialTheme.shapes.extraLarge
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxWidth()) {
        // Soft edge fade keeps scrolled rows from peeking harshly in the gap below the floating pill.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navBottom + 24.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, backgroundColor.copy(alpha = 0.94f)))),
        )
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        ) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val labelStyle = MaterialTheme.typography.labelMedium
            val tabs = MainTab.entries
            val labels = tabs.map { stringResource(it.label) }
            val viewport = constraints.maxWidth
            val edge = with(density) { 4.dp.roundToPx() }
            val widths = remember(labels, measurer, labelStyle, density, viewport) {
                with(density) {
                    val minimumTouch = 48.dp.roundToPx()
                    val maxCell = (viewport - edge * 2).coerceAtLeast(minimumTouch)
                    val minimums = labels.map { label ->
                        val textWidth = measurer.measure(label, style = labelStyle, softWrap = false).size.width
                        (textWidth + 2.dp.roundToPx() * 2).coerceIn(minimumTouch, maxCell)
                    }
                    val spare = (viewport - edge * 2 - minimums.sum()).coerceAtLeast(0)
                    minimums.mapIndexed { index, width ->
                        width + spare / tabs.size + if (index < spare % tabs.size) 1 else 0
                    }
                }
            }
            val scroll = rememberScrollState()
            val overflow = widths.sum() + edge * 2 > viewport
            LaunchedEffect(selected, widths, viewport) {
                if (!overflow) return@LaunchedEffect
                snapshotFlow { scroll.viewportSize }.first { it == viewport }
                var start = edge
                for (index in 0 until selected.ordinal) start += widths[index]
                val end = start + widths[selected.ordinal]
                val target = when {
                    start < scroll.value -> start
                    end > scroll.value + viewport -> end - viewport
                    else -> return@LaunchedEffect
                }
                val animatorScale = runCatching {
                    android.provider.Settings.Global.getFloat(
                        context.contentResolver,
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f,
                    )
                }.getOrDefault(1f)
                if (animatorScale > 0f) scroll.animateScrollTo(target) else scroll.scrollTo(target)
            }
            GlassChromeSurface(
                hazeState = hazeState,
                style = glassStyle,
                shape = dockShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(scroll)
                            .height(IntrinsicSize.Max)
                            .padding(top = 34.dp, bottom = if (overflow) 2.dp else 7.dp, start = 4.dp, end = 4.dp)
                            .selectableGroup(),
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val width = with(density) { widths[index].toDp() }
                            TabItem(tab, tab == selected, { onSelect(tab) }, Modifier.width(width).fillMaxHeight())
                        }
                    }
                    if (overflow) {
                        val colors = MaterialTheme.colorScheme
                        Box(
                            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 3.dp)
                                .fillMaxWidth()
                                .height(2.dp)
                                .drawBehind {
                                    val range = scroll.maxValue
                                    if (range <= 0 || range == Int.MAX_VALUE) return@drawBehind
                                    val radius = CornerRadius(size.height / 2f)
                                    val thumb = size.width * viewport / (viewport + range)
                                    val progress = scroll.value.toFloat() / range
                                    val offset = if (layoutDirection == LayoutDirection.Ltr) progress else 1f - progress
                                    drawRoundRect(colors.outlineVariant, cornerRadius = radius)
                                    drawRoundRect(
                                        colors.onSurfaceVariant,
                                        topLeft = Offset((size.width - thumb) * offset, 0f),
                                        size = Size(thumb, size.height),
                                        cornerRadius = radius,
                                    )
                                },
                        )
                    }
                }
            }
            RecordAction(onRecord, Modifier.align(Alignment.TopCenter))
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
    val view = LocalView.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.large
    val colors = MaterialTheme.colorScheme
    val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 54.dp)
            .focusOutline(focused, shape, colors.primary, colors.surface)
            .clip(shape)
            .then(if (selected) Modifier.background(colors.primaryContainer, shape) else Modifier)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = {
                    if (!selected) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                },
                interactionSource = interactions,
                indication = LocalIndication.current,
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(width = 48.dp, height = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(tab.icon),
                null,
                Modifier.size(20.dp),
                tint = contentColor,
            )
        }
        Text(
            stringResource(tab.label),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = contentColor,
        )
    }
}
