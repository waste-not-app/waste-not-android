package com.example.wastenot.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.repository.ThemeStore
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

enum class ThemeFailure { READ, WRITE }

data class ThemeUiState(val mode: ThemeMode? = null, val saving: Boolean = false, val failure: ThemeFailure? = null)

class ThemeViewModel(private val store: ThemeStore) : ViewModel() {
    private val mutableState = MutableStateFlow(ThemeUiState())
    val state = mutableState.asStateFlow()
    private var observing: Job? = null
    private var started = false
    private var readGeneration = 0
    private var intent = 0
    private var requested: ThemeMode? = null
    private val writes = Mutex()

    fun load(retry: Boolean = false) {
        if (started && !retry) return
        started = true
        val stamp = ++readGeneration
        observing?.cancel()
        observing = viewModelScope.launch {
            try {
                val initial = withTimeout(5_000) { store.modes.first() }
                received(initial, stamp)
                store.modes.collect { received(it, stamp) }
            } catch (_: IOException) {
                readFailed(stamp)
            } catch (_: TimeoutCancellationException) {
                readFailed(stamp)
            }
        }
    }

    fun choose(mode: ThemeMode) {
        if (mode == state.value.mode && state.value.failure == null) return
        val stamp = ++intent
        requested = mode
        mutableState.update { it.copy(mode = mode, saving = true, failure = null) }
        viewModelScope.launch {
            writes.withLock {
                if (stamp != intent) return@withLock
                try {
                    store.write(mode)
                    currentCoroutineContext().ensureActive()
                    if (stamp == intent) {
                        mutableState.update { it.copy(saving = false, failure = null) }
                        load(retry = true)
                    }
                } catch (_: IOException) {
                    currentCoroutineContext().ensureActive()
                    if (stamp == intent) mutableState.update { it.copy(saving = false, failure = ThemeFailure.WRITE) }
                }
            }
        }
    }

    fun retry() {
        if (state.value.failure == ThemeFailure.WRITE) requested?.let(::choose)
        else load(retry = true)
    }

    private suspend fun received(mode: ThemeMode, stamp: Int) {
        currentCoroutineContext().ensureActive()
        if (stamp != readGeneration) return
        mutableState.update {
            if (it.saving || it.failure == ThemeFailure.WRITE) it
            else it.copy(mode = mode, failure = null)
        }
    }

    private fun readFailed(stamp: Int) {
        if (stamp != readGeneration) return
        mutableState.update {
            if (it.saving || it.failure == ThemeFailure.WRITE) it
            else it.copy(mode = it.mode ?: ThemeMode.SYSTEM, failure = ThemeFailure.READ)
        }
    }
}
