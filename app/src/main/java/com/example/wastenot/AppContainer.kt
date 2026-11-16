package com.example.wastenot

import android.content.Context
import com.example.wastenot.model.User
import com.example.wastenot.data.local.ScanPhotoCache
import com.example.wastenot.data.repository.RemoteScanRepository
import com.example.wastenot.data.repository.ScanRepository
import com.example.wastenot.data.repository.RemoteActivityStore
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.data.repository.AccountSession
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.wastenot.data.local.KeystoreRefreshTokenStore
import com.example.wastenot.data.local.ThemePreferences
import com.example.wastenot.data.repository.ThemeStore
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.data.repository.AiRepository
import com.example.wastenot.data.repository.SessionRepository
import com.example.wastenot.data.repository.RemoteAccountSession
import com.example.wastenot.data.repository.GuideStore
import com.example.wastenot.data.repository.RemoteGuideStore
import com.example.wastenot.data.local.ActivityDatabase
import com.example.wastenot.data.local.ActivitySyncScheduler
import com.example.wastenot.data.local.RoomSessionIdentityStore
import com.example.wastenot.data.repository.OfflineActivityStore
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.data.local.ReminderPreferences
import com.example.wastenot.data.local.ReminderScheduler
import com.example.wastenot.data.local.ReminderStore
import com.example.wastenot.data.local.WorkManagerReminderScheduler
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import com.example.wastenot.data.repository.AccountManagement
import com.example.wastenot.data.repository.RemoteAccountManagement
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.local.LocalEditorDraftStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    open val showOnboarding: Boolean = true
    open fun onboardingCompleted(): Boolean =
        appContext.getSharedPreferences("wastenot_onboarding", Context.MODE_PRIVATE).getBoolean("completed", false)
    open fun completeOnboarding() {
        appContext.getSharedPreferences("wastenot_onboarding", Context.MODE_PRIVATE).edit().putBoolean("completed", true).apply()
    }
    open val appearance: ThemeStore = ThemePreferences.create(context)
    private val network = WasteNotNetwork.create(BuildConfig.API_BASE_URL, allowLocalHttp = BuildConfig.DEBUG)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val database by lazy { ActivityDatabase.create(context) }
    private val scheduler by lazy { ActivitySyncScheduler(context) }
    open val reminders: ReminderStore = ReminderPreferences.create(context)
    open val reminderScheduler: ReminderScheduler = WorkManagerReminderScheduler(context)
    val sessions = SessionRepository(
        network = network,
        tokenStore = KeystoreRefreshTokenStore.create(context),
        identityStore = RoomSessionIdentityStore(database.activities()),
        clearAccountData = { userId, _ ->
            reminderScheduler.cancel(userId)
            reminders.clear(userId)
        },
    )
    open val accountSession: AccountSession =
        RemoteAccountSession(sessions, ::networkAvailable)
    val core = CoreRepository(network, sessions)
    val ai = AiRepository(network, sessions)
    open val guides: GuideStore = RemoteGuideStore(core)

    private val activities = ConcurrentHashMap<String, OfflineActivityStore>()
    open val accountManagement: AccountManagement = RemoteAccountManagement(network, sessions, ::clearDeletedAccount)

    private suspend fun clearDeletedAccount(owner: String) = withContext(Dispatchers.IO) {
        scheduler.cancel(owner)
        try {
            val store = activities.remove(owner)
            if (store != null) store.deleteLocalData() else database.activities().clearOwner(owner)
        } catch (error: android.database.sqlite.SQLiteException) {
            throw IOException("Unable to clear deleted account storage", error)
        }
        if (LocalEditorDraftStore.create(appContext, owner).clear() is ApiResult.Failure) {
            throw IOException("Unable to clear deleted account draft")
        }
        if (!ScanPhotoCache.create(appContext, owner).removeOwnerPhotos()) {
            throw IOException("Unable to clear deleted account photo cache")
        }
    }

    open fun activityStore(user: User): ActivityStore = offlineActivities(user).also { it.requestSync() }

    private fun offlineActivities(user: User) = activities.getOrPut(user.id) {
        OfflineActivityStore(user, database, RemoteActivityStore(core),
            isOwnerActive = { (sessions.state.value as? SessionState.Authenticated)?.user?.id == user.id },
            schedule = { scheduler.enqueue(user.id) })
    }

    suspend fun synchronizeActivities(owner: String): Boolean {
        // A worker never bootstraps a different account, even if its old outbox still exists.
        val phase = sessions.state.value
        if (phase is SessionState.Checking) {
            if (database.activities().identity()?.owner != owner) return true
            sessions.bootstrap(networkAvailable())
        }
        var active = sessions.state.value as? SessionState.Authenticated ?: return true
        if (active.user.id != owner) return true
        if (active.offline) {
            if (!networkAvailable()) return false
            sessions.bootstrap(networkAvailable = true)
            active = sessions.state.value as? SessionState.Authenticated ?: return true
        }
        if (active.user.id != owner || active.offline) return false
        return offlineActivities(active.user).synchronize()
    }

    private fun networkAvailable(): Boolean = connectivity?.activeNetwork?.let {
        connectivity.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } == true

    suspend fun isReminderEligible(owner: String, zoneId: ZoneId): Boolean {
        val activeId = (sessions.state.value as? SessionState.Authenticated)?.user?.id
            ?: RoomSessionIdentityStore(database.activities()).read()?.id
        if (activeId != owner) return false
        val today = LocalDate.now(zoneId).toString()
        val localToday = database.activities().page(owner, null, null, today, today, 1, 0)
        if (localToday.isNotEmpty()) return false
        if (networkAvailable()) {
            val dash = core.dashboard()
            if (dash is com.example.wastenot.data.network.ApiResult.Success && dash.value.progress.hasLoggedToday) {
                return false
            }
        }
        return true
    }

    open fun scanRepository(cache: ScanPhotoCache): ScanRepository =
        RemoteScanRepository(ai, cache)
}
