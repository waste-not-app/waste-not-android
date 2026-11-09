package com.example.wastenot.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

@Composable
internal fun rememberAccountDate(zone: ZoneId, referenceDate: LocalDate?, clock: Clock): State<LocalDate> {
    val date = remember(zone, referenceDate, clock) {
        mutableStateOf(referenceDate ?: LocalDate.now(clock.withZone(zone)))
    }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(zone, referenceDate, clock, owner) {
        if (referenceDate == null) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                accountDateChanges(zone, clock).collect { date.value = it }
            }
        }
    }
    return date
}

internal fun accountDateChanges(zone: ZoneId, clock: Clock): Flow<LocalDate> = flow {
    while (currentCoroutineContext().isActive) {
        val now = clock.instant()
        val date = now.atZone(zone).toLocalDate()
        emit(date)
        val midnight = date.plusDays(1).atStartOfDay(zone).toInstant()
        delay(Duration.between(now, midnight).toMillis().coerceIn(1L, 60_000L))
    }
}.distinctUntilChanged()
