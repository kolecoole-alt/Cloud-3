package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    // ---- Logs queries ----
    @Query("SELECT * FROM alarm_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<AlarmLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AlarmLog)

    @Query("DELETE FROM alarm_logs")
    suspend fun clearAllLogs()

    // ---- Settings queries ----
    @Query("SELECT * FROM alarm_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AlarmSettings?>

    @Query("SELECT * FROM alarm_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsSync(): AlarmSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: AlarmSettings)
}
