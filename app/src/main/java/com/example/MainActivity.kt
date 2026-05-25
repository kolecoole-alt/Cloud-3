package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AlarmLog
import com.example.data.AlarmRepository
import com.example.data.AlarmSettings
import com.example.data.AppDatabase
import com.example.service.AlarmService
import com.example.service.AlarmState
import com.example.service.AlarmStateTracker
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- ViewModel ----------------

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AlarmRepository
    
    val allLogs: StateFlow<List<AlarmLog>>
    val settings: StateFlow<AlarmSettings>
    
    val alarmState: StateFlow<AlarmState> = AlarmStateTracker.state
    val countdown: StateFlow<Int> = AlarmStateTracker.countdown

    init {
        val db = AppDatabase.getDatabase(application)
        repository = AlarmRepository(db.alarmDao())
        
        allLogs = repository.allLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        settings = repository.settings
            .map { it ?: AlarmSettings() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AlarmSettings()
            )
    }

    fun updateOwnerName(name: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(ownerName = name))
        }
    }

    fun updateSensitivity(value: Float) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(sensitivity = value))
        }
    }

    fun updateArmingDelay(delaySeconds: Int) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(armingDelay = delaySeconds))
        }
    }

    fun updateCustomSpeechPhrase(phrase: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(customSpeechPhrase = phrase))
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun toggleArmedState(context: Context) {
        val currentState = alarmState.value
        if (currentState == AlarmState.UNARMED) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_START_ARMING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = AlarmService.ACTION_DISARM
            }
            context.startService(intent)
        }
    }
}

// ---------------- MainActivity ----------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

// ---------------- Composable UI Elements ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AlarmViewModel) {
    val context = LocalContext.current
    val currentAlarmState by viewModel.alarmState.collectAsState()
    val countdownValue by viewModel.countdown.collectAsState()
    val currentSettings by viewModel.settings.collectAsState()
    val recentLogs by viewModel.allLogs.collectAsState()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFFFB4AB), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Security Guard Header Shield",
                                tint = Color(0xFF690005),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Ne Touche Pas !",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Système de protection actif",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Vider l'historique",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF16151A),
                            Color(0xFF0A0A0A)
                        )
                    )
                )
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Alarm Indicator Component with interactive toggle built-in
            AlarmIndicator(
                state = currentAlarmState,
                countdown = countdownValue,
                onToggleArmed = { viewModel.toggleArmedState(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            // Dynamic Action Button
            Button(
                onClick = { viewModel.toggleArmedState(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("arm_toggle_button"),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (currentAlarmState) {
                        AlarmState.UNARMED -> MaterialTheme.colorScheme.primary
                        AlarmState.ARMING -> MaterialTheme.colorScheme.error
                        AlarmState.ARMED -> Color(0xFF93000A)
                        AlarmState.TRIGGERED -> MaterialTheme.colorScheme.error
                    },
                    contentColor = when (currentAlarmState) {
                        AlarmState.UNARMED -> MaterialTheme.colorScheme.onPrimary
                        AlarmState.ARMING -> MaterialTheme.colorScheme.onError
                        AlarmState.ARMED -> Color(0xFFFFDAD6)
                        AlarmState.TRIGGERED -> MaterialTheme.colorScheme.onError
                    }
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                border = if (currentAlarmState == AlarmState.ARMED) BorderStroke(1.dp, Color(0xFFFFDAD6).copy(alpha = 0.5f)) else null
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (currentAlarmState) {
                            AlarmState.UNARMED -> Icons.Default.PlayArrow
                            AlarmState.ARMING -> Icons.Default.Stop
                            AlarmState.ARMED -> Icons.Default.Stop
                            AlarmState.TRIGGERED -> Icons.Default.Stop
                        },
                        contentDescription = "Bouton d'armement",
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (currentAlarmState) {
                            AlarmState.UNARMED -> "ACTIVER LA SURVEILLANCE"
                            AlarmState.ARMING -> "ANNULER L'ARMEMENT"
                            AlarmState.ARMED -> "DÉSACTIVER L'ALARME"
                            AlarmState.TRIGGERED -> "ARRÊTER L'ALARME (DÉSARMER)"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Configuration Options Panel
            SettingsPanel(
                settings = currentSettings,
                enabled = currentAlarmState == AlarmState.UNARMED,
                onNameChange = { viewModel.updateOwnerName(it) },
                onSensitivityChange = { viewModel.updateSensitivity(it) },
                onDelayChange = { viewModel.updateArmingDelay(it) },
                onPhraseChange = { viewModel.updateCustomSpeechPhrase(it) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // IncidentLogs Panel
            LogsPanel(
                logs = recentLogs,
                onClear = { viewModel.clearHistory() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AlarmIndicator(
    state: AlarmState, 
    countdown: Int, 
    onToggleArmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    
    // Pulse animation factor for active alarms
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (state == AlarmState.TRIGGERED) 1.2f else if (state == AlarmState.ARMED) 1.05f else 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (state == AlarmState.TRIGGERED) 420 else 2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAnimation"
    )

    val animatedColor by animateColorAsState(
        targetValue = when (state) {
            AlarmState.UNARMED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            AlarmState.ARMING -> Color(0xFFFFB300) // Amber
            AlarmState.ARMED -> Color(0xFF93000A) // Armed rich red
            AlarmState.TRIGGERED -> Color(0xFFF44336) // Flashing Red
        },
        animationSpec = tween(durationMillis = 350),
        label = "ColorIndicator"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Concentric design layout
        Box(
            modifier = Modifier
                .padding(vertical = 32.dp)
                .size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulse Ring 2 (Outer)
            Box(
                modifier = Modifier
                    .scale(pulseScale * 1.25f)
                    .size(220.dp)
                    .border(
                        1.5.dp,
                        if (state == AlarmState.ARMED || state == AlarmState.TRIGGERED) 
                            Color(0xFFFFB4AB).copy(alpha = 0.1f) 
                        else 
                            animatedColor.copy(alpha = 0.05f),
                        CircleShape
                    )
            )

            // Pulse Ring 1 (Inner)
            Box(
                modifier = Modifier
                    .scale(pulseScale * 1.1f)
                    .size(200.dp)
                    .border(
                        1.5.dp,
                        if (state == AlarmState.ARMED || state == AlarmState.TRIGGERED) 
                            Color(0xFFFFB4AB).copy(alpha = 0.2f) 
                        else 
                            animatedColor.copy(alpha = 0.12f),
                        CircleShape
                    )
            )

            // Main Active Toggle Button Circle
            Card(
                modifier = Modifier
                    .size(176.dp)
                    .scale(pulseScale)
                    .testTag("arm_indicator_circle_button")
                    .clickable { onToggleArmed() },
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = when (state) {
                        AlarmState.UNARMED -> MaterialTheme.colorScheme.surfaceVariant
                        AlarmState.ARMING -> Color(0xFFFFB300)
                        AlarmState.ARMED -> Color(0xFF93000A)
                        AlarmState.TRIGGERED -> Color(0xFFEE2222)
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = BorderStroke(
                    2.dp, 
                    when (state) {
                        AlarmState.UNARMED -> MaterialTheme.colorScheme.outline
                        AlarmState.ARMING -> Color.White.copy(alpha = 0.6f)
                        AlarmState.ARMED -> Color(0xFFFFDAD6)
                        AlarmState.TRIGGERED -> Color(0xFFFFB4AB)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = when (state) {
                            AlarmState.UNARMED -> Icons.Default.LockOpen
                            AlarmState.ARMING -> Icons.Default.Warning
                            AlarmState.ARMED -> Icons.Default.Lock
                            AlarmState.TRIGGERED -> Icons.Default.NotificationsActive
                        },
                        contentDescription = "Shield Status Indicator",
                        tint = when (state) {
                            AlarmState.UNARMED -> MaterialTheme.colorScheme.onSurfaceVariant
                            AlarmState.ARMING -> Color.Black
                            AlarmState.ARMED -> Color(0xFFFFDAD6)
                            AlarmState.TRIGGERED -> Color.White
                        },
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = when (state) {
                            AlarmState.UNARMED -> "DÉSACTIVÉ"
                            AlarmState.ARMING -> "ARMEMENT"
                            AlarmState.ARMED -> "ARMÉ"
                            AlarmState.TRIGGERED -> "ALERTE !"
                        },
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = when (state) {
                            AlarmState.UNARMED -> MaterialTheme.colorScheme.onSurface
                            AlarmState.ARMING -> Color.Black
                            AlarmState.ARMED -> Color(0xFFFFDAD6)
                            AlarmState.TRIGGERED -> Color.White
                        }
                    )
                    if (state == AlarmState.ARMING) {
                        Text(
                            text = "${countdown}s",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.Black.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Subtitle status display card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    0.5.dp, 
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), 
                    RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (state) {
                        AlarmState.UNARMED -> "SURVEILLANCE INACTIVE"
                        AlarmState.ARMING -> "ARMEMENT EN COURS"
                        AlarmState.ARMED -> "PROTÉGÉ COUVERT"
                        AlarmState.TRIGGERED -> "INTENTION DE CONTACT FILTRÉE !"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (state) {
                        AlarmState.UNARMED -> MaterialTheme.colorScheme.primary
                        AlarmState.ARMING -> Color(0xFFFFB300)
                        AlarmState.ARMED -> Color(0xFFEADDFF)
                        AlarmState.TRIGGERED -> Color(0xFFF2B8B5)
                    },
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = when (state) {
                        AlarmState.UNARMED -> "Configurez vos réglages ci-dessous et armez pour protéger votre téléphone contre les curieux."
                        AlarmState.ARMING -> "Posez le téléphone à plat. Fixation de l'équilibre dans $countdown secondes..."
                        AlarmState.ARMED -> "Armé avec succès ! Si quelqu'un touche ou déplace le matériel, l'alarme sera déclenchée immédiatement."
                        AlarmState.TRIGGERED -> "Alerte de sécurité activée ! La sirène vocale et les vibrations signalent l'intrus."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}


@Composable
fun SettingsPanel(
    settings: AlarmSettings,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onDelayChange: (Int) -> Unit,
    onPhraseChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configuration icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Réglages de Protection",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (!enabled) {
                Text(
                    text = "⚠️ Désactivez la surveillance pour modifier les paramètres",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Owner Name Field
            OutlinedTextField(
                value = settings.ownerName,
                onValueChange = { onNameChange(it) },
                label = { Text("Votre Nom (utilisé pour l'appel)") },
                placeholder = { Text("Ex: Bilal") },
                singleLine = true,
                enabled = enabled,
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Person, contentDescription = "Nom de l'utilisateur")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("owner_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Custom TTS Announcement Field
            OutlinedTextField(
                value = settings.customSpeechPhrase,
                onValueChange = { onPhraseChange(it) },
                label = { Text("Phrase d'alarme personnalisée") },
                placeholder = { Text("Ex: Pas touche, Bilal dit non !") },
                singleLine = false,
                maxLines = 2,
                enabled = enabled,
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = "Phrase personnalisée")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_phrase_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Sensitivity Slider
            val sliderValue = when {
                settings.sensitivity <= 1.2f -> 2f // Élevée
                settings.sensitivity >= 4.0f -> 0f // Faible
                else -> 1f // Moyenne (default 2.5f)
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sensibilité au contact",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (sliderValue.toInt()) {
                            0 -> "Faible (Shocks robustes)"
                            1 -> "Moyenne (Soulèvement)"
                            else -> "Élevée (Effleurement)"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { newValue ->
                        val mappedSensitivity = when (newValue.toInt()) {
                            0 -> 4.5f // Faible sensitivity (Needs high delta)
                            1 -> 2.5f // Moyenne
                            else -> 1.0f  // Élevée sensitivity (Triggers easily)
                        }
                        onSensitivityChange(mappedSensitivity)
                    },
                    valueRange = 0f..2f,
                    steps = 1,
                    enabled = enabled,
                    modifier = Modifier.testTag("sensitivity_slider")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Delay configuration
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Délai d'armement",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${settings.armingDelay} secondes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.armingDelay.toFloat(),
                    onValueChange = { onDelayChange(it.toInt()) },
                    valueRange = 2f..15f,
                    steps = 12,
                    enabled = enabled,
                    modifier = Modifier.testTag("arming_delay_slider")
                )
            }
        }
    }
}

@Composable
fun LogsPanel(
    logs: List<AlarmLog>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Journal d'alertes",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Journal des Intrusions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (logs.isNotEmpty()) {
                    Text(
                        text = "Effacer",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onClear() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun incident détecté. Votre téléphone est en sécurité ! 👍",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Display latest items directly inside the scrollable column
                logs.take(5).forEach { log ->
                    LogItemRow(log = log)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (logs.size > 5) {
                    Text(
                        text = "+ ${logs.size - 5} autres alertes enregistrées...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: AlarmLog) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Contact Alerte",
            tint = Color(0xFFF44336),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = log.eventType,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = log.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatTimestamp(log.timestamp),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy 'à' HH:mm:ss", Locale.FRENCH)
    return sdf.format(Date(timestamp))
}

// Border Stroke Helper since standard is part of drawing libs
@Composable
fun BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = 
    androidx.compose.foundation.BorderStroke(width, color)
