package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AlarmState {
    UNARMED,
    ARMING,
    ARMED,
    TRIGGERED
}

object AlarmStateTracker {
    private val _state = MutableStateFlow(AlarmState.UNARMED)
    val state = _state.asStateFlow()

    private val _countdown = MutableStateFlow(0)
    val countdown = _countdown.asStateFlow()

    fun updateState(newState: AlarmState) {
        _state.value = newState
    }

    fun updateCountdown(count: Int) {
        _countdown.value = count
    }
}
