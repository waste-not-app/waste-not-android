package com.example.wastenot

import android.content.Context
import android.net.ConnectivityManager
import com.example.wastenot.data.local.KeystoreRefreshTokenStore
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.data.repository.SessionRepository
import com.example.wastenot.data.repository.RemoteAccountSession

class AppContainer(context: Context) {
    private val network = WasteNotNetwork.create(BuildConfig.API_BASE_URL, allowLocalHttp = BuildConfig.DEBUG)
    val sessions = SessionRepository(network, KeystoreRefreshTokenStore.create(context))
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    val accountSession = RemoteAccountSession(sessions) { connectivity?.activeNetwork != null }
    val core = CoreRepository(network, sessions)
}
