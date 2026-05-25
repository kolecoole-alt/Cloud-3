package com.example.data

import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val alarmDao: AlarmDao) {
    val allLogs: Flow<List<AlarmLog>> = alarmDao.getAllLogsFlow()
    val settings: Flow<AlarmSettings?> = alarmDao.getSettingsFlow()

    suspend fun getSettingsDirect(): AlarmSettings {
        return alarmDao.getSettingsSync() ?: AlarmSettings()
    }

    suspend fun saveSettings(alarmSettings: AlarmSettings) {
        alarmDao.updateSettings(alarmSettings)
    }

    suspend fun insertLog(log: AlarmLog) {
        alarmDao.insertLog(log)
    }

    suspend fun clearLogs() {
        alarmDao.clearAllLogs()
    }
}
