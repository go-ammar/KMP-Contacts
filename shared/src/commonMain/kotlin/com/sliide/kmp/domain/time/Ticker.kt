package com.sliide.kmp.domain.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/**
 * Emits on a fixed interval, forever. Injected into the list ViewModel rather than looped
 * inline so tests can substitute an empty or finite flow instead of fighting an endless delay.
 */
fun periodicTicker(interval: Duration): Flow<Unit> =
    flow {
        while (true) {
            delay(interval)
            emit(Unit)
        }
    }
