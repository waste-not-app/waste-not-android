package com.example.wastenot

import android.app.Application

class WasteNotApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
