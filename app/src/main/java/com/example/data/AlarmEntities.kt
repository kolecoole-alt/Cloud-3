package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_logs")
data class AlarmLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String = "Mouvement détecté",
    val description: String = "Quelqu'un a touché le téléphone"
)

@Entity(tableName = "alarm_settings")
data class AlarmSettings(
    @PrimaryKey val id: Int = 1, // Single row settings
    val ownerName: String = "Bilal",
    val sensitivity: Float = 2.5f, // Shaking magnitude delta threshold
    val armingDelay: Int = 5, // Arms after 5 seconds count-down
    val isArmed: Boolean = false,
    val isVibrationEnabled: Boolean = true,
    val isTtsEnabled: Boolean = true,
    val customSpeechPhrase: String = "Arrête de toucher à mon téléphone !"
)
