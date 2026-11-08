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

open class AppContainer(context: Context) {
    open val appearance: ThemeStore = ThemePreferences.create(context)
    private val network = WasteNotNetwork.create(BuildConfig.API_BASE_URL, allowLocalHttp = BuildConfig.DEBUG)
    val sessions = SessionRepository(network, KeystoreRefreshTokenStore.create(context))
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    open val accountSession: AccountSession =
        RemoteAccountSession(sessions) { connectivity?.activeNetwork != null }
    val core = CoreRepository(network, sessions)
    val ai = AiRepository(network, sessions)
    open val guides: GuideStore = RemoteGuideStore(core)

    open fun activityStore(user: User): ActivityStore =
        RemoteActivityStore(core)

    open fun scanRepository(cache: ScanPhotoCache): ScanRepository =
        RemoteScanRepository(ai, cache)
}
