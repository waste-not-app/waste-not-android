package com.example.wastenot

import android.content.Context
import com.example.wastenot.data.local.KeystoreRefreshTokenStore
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.data.repository.SessionRepository

class AppContainer(context: Context) {
    private val network = WasteNotNetwork.create(BuildConfig.API_BASE_URL, allowLocalHttp = BuildConfig.DEBUG)
    val sessions = SessionRepository(network, KeystoreRefreshTokenStore.create(context))
    val core = CoreRepository(network, sessions)
}
