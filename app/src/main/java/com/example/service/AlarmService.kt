package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AlarmLog
import com.example.data.AlarmRepository
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale

class AlarmService : Service(), SensorEventListener {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var repository: AlarmRepository

    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private lateinit var vibrator: Vibrator
    private var speechLoopJob: Job? = null
    private var countdownJob: Job? = null

    // Accelerometer variables
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var hasInitialValues = false
    private var threshold = 2.5f // Set dynamically from settings

    companion object {
        private const val NOTIFICATION_ID = 4512
        const val CHANNEL_ID = "anti_touch_alarm_channel"
        
        const val ACTION_START_ARMING = "ACTION_START_ARMING"
        const val ACTION_DISARM = "ACTION_DISARM"
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val db = AppDatabase.getDatabase(this)
        repository = AlarmRepository(db.alarmDao())

        initVibrator()
        initTextToSpeech()
        createNotificationChannel()
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.FRENCH)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.language = Locale.getDefault()
                }
                isTtsInitialized = true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_ARMING

        if (action == ACTION_DISARM) {
            stopAlarmAndSelf()
            return START_NOT_STICKY
        }

        // Start Foreground Notification first
        startForeground(NOTIFICATION_ID, buildNotification("Préparation de la surveillance...", "Veuillez patienter"))

        if (action == ACTION_START_ARMING) {
            startArmingFlow()
        }

        return START_NOT_STICKY
    }

    private fun startArmingFlow() {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            // Read settings from Database
            val settings = repository.settings.firstOrNull() ?: repository.getSettingsDirect()
            threshold = settings.sensitivity
            val delaySeconds = settings.armingDelay
            
            AlarmStateTracker.updateState(AlarmState.ARMING)
            
            // Beep or announce countdown start (TTS might not be fully initialized yet, so we count down)
            for (i in delaySeconds downTo 1) {
                AlarmStateTracker.updateCountdown(i)
                updateNotification(
                    "Armement imminent...", 
                    "S'active dans $i secondes. Posez le téléphone."
                )
                
                // Play a brief notification vibrate as warning beep
                vibrateTick()
                delay(1000)
            }

            // Arm the system!
            AlarmStateTracker.updateCountdown(0)
            AlarmStateTracker.updateState(AlarmState.ARMED)
            updateNotification(
                "Surveillance Active 🛡️", 
                "Prêt ! Ne touchez pas à mon téléphone !"
            )
            
            // Speak confirmation "Sécurité activée"
            if (isTtsInitialized) {
                textToSpeech?.speak("Sécurité activée !", TextToSpeech.QUEUE_FLUSH, null, "ArmedConfirm")
            }

            // Long vibration to signal armed status
            vibrateConfirmation()
            
            // Reset sensor readings so we register next movements accurately
            hasInitialValues = false
            sensorManager.registerListener(
                this@AlarmService, 
                accelerometer, 
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    private fun triggerAlarm() {
        if (AlarmStateTracker.state.value == AlarmState.ARMED) {
            AlarmStateTracker.updateState(AlarmState.TRIGGERED)
            updateNotification(
                "🚨 ALERTE DETECTÉE !", 
                "Quelqu'un touche au téléphone ! Stop !"
            )

            // Save record in database
            serviceScope.launch {
                val settings = repository.getSettingsDirect()
                val logText = "Mouvement détecté - Seuil dépassé (${String.format("%.2f", threshold)})"
                repository.insertLog(AlarmLog(eventType = "Alerte de contact", description = logText))
                
                // Trigger repeat TTS and continuous vibration
                startSirenCapabilities(settings.ownerName, settings.customSpeechPhrase)
            }
        }
    }

    private fun startSirenCapabilities(userName: String, customPhrase: String) {
        // Continuous shaking vibration pattern
        val pattern = longArrayOf(0, 1000, 500, 1000, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }

        // TTS text combination which called the name and says stop!
        val speechText = "Arrête ! Ne touche pas au téléphone de $userName ! Stop ! Pose ce téléphone !"
        val phrasesToSpeak = if (customPhrase.trim().isNotEmpty()) {
            "$customPhrase $userName dit stop !"
        } else {
            speechText
        }

        speechLoopJob?.cancel()
        speechLoopJob = serviceScope.launch {
            while (AlarmStateTracker.state.value == AlarmState.TRIGGERED) {
                if (isTtsInitialized) {
                    textToSpeech?.speak(phrasesToSpeak, TextToSpeech.QUEUE_FLUSH, null, "SirenLoop")
                }
                delay(4000) // Repeat every 4 seconds
            }
        }
    }

    private fun stopAlarmAndSelf() {
        sensorManager.unregisterListener(this)
        countdownJob?.cancel()
        speechLoopJob?.cancel()
        vibrator.cancel()
        
        if (isTtsInitialized) {
            textToSpeech?.stop()
        }

        AlarmStateTracker.updateState(AlarmState.UNARMED)
        AlarmStateTracker.updateCountdown(0)
        
        stopForeground(true)
        stopSelf()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (hasInitialValues) {
            val deltaX = Math.abs(x - lastX)
            val deltaY = Math.abs(y - lastY)
            val deltaZ = Math.abs(z - lastZ)
            val totalDelta = deltaX + deltaY + deltaZ

            if (AlarmStateTracker.state.value == AlarmState.ARMED && totalDelta > threshold) {
                triggerAlarm()
            }
        }

        lastX = x
        lastY = y
        lastZ = z
        hasInitialValues = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    // Vibration Utilities
    private fun vibrateTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    private fun vibrateConfirmation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    // Foreground service notification wrappers
    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Standard launcher foreground for placeholder
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alerte Anti-Intrusion"
            val descriptionText = "Indicateur d'alarme active"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(false) // Handle manually for security/siren volume control
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAlarmAndSelf()
        serviceJob.cancel()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
