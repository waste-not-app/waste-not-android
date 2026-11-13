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
import java.util.concurrent.ConcurrentHashMap

open class AppContainer(context: Context) {
    open val appearance: ThemeStore = ThemePreferences.create(context)
    private val network = WasteNotNetwork.create(BuildConfig.API_BASE_URL, allowLocalHttp = BuildConfig.DEBUG)
    private val database by lazy { ActivityDatabase.create(context) }
    private val scheduler by lazy { ActivitySyncScheduler(context) }
    val sessions = SessionRepository(network, KeystoreRefreshTokenStore.create(context),
        identityStore = RoomSessionIdentityStore(database.activities()))
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    open val accountSession: AccountSession =
        RemoteAccountSession(sessions, ::networkAvailable)
    val core = CoreRepository(network, sessions)
    val ai = AiRepository(network, sessions)
    open val guides: GuideStore = RemoteGuideStore(core)

    private val activities = ConcurrentHashMap<String, OfflineActivityStore>()

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

    open fun scanRepository(cache: ScanPhotoCache): ScanRepository =
        RemoteScanRepository(ai, cache)
}
