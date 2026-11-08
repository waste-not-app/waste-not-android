package com.example.wastenot

import android.app.Application

open class WasteNotApplication : Application() {
    open val container: AppContainer by lazy { AppContainer(this) }
}
