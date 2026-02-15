package com.example.domawearassist.presentation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.example.domawearassist.presentation.theme.DomaWearAssistTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private var ttsReady by mutableStateOf(false)
    private lateinit var audioManager: AudioManager
    private lateinit var audioHelper: AudioHelper

    private var lastMessageSpoken: String? = null

    private companion object {
        const val NotificationChannelId = "doma_notifications"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioHelper = AudioHelper(this)

        createNotificationChannel()

        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.getDefault())
                ttsReady = !(result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED)
                if (ttsReady) speak("Sistema de áudio ativado")
            } else {
                ttsReady = false
            }
        }

        setContent {
            val context = LocalContext.current
            var hasNotificationPermission by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else true
                )
            }

            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasNotificationPermission = isGranted
            }

            LaunchedEffect(Unit) {
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val bluetoothState = remember {
                mutableStateOf(audioHelper.audioOutputAvailable(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
            }

            DisposableEffect(Unit) {
                val callback = object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                        bluetoothState.value = audioHelper.audioOutputAvailable(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
                    }
                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                        bluetoothState.value = audioHelper.audioOutputAvailable(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
                    }
                }
                audioManager.registerAudioDeviceCallback(callback, null)
                onDispose {
                    audioManager.unregisterAudioDeviceCallback(callback)
                }
            }

            WearApp(
                greetingName = "Android",
                isTtsReady = ttsReady,
                bluetoothConnected = bluetoothState.value,
                onSpeak = { speak(it) },
                onSendNotification = { title, msg -> sendVisualNotification(title, msg) },
                onOpenBluetoothSettings = { openBluetoothSettings() },
                toggleBluetoothSimulated = { bluetoothState.value = !bluetoothState.value }
            )
        }
    }

    private fun createNotificationChannel() {
        val name = "Doma Assist"
        val descriptionText = "Notificações do Sistema Doma"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(NotificationChannelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun sendVisualNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, NotificationChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun speak(message: String) {
        if (lastMessageSpoken == message) return
        lastMessageSpoken = message

        if (ttsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
        } else {
            Log.d("TTS", "🔊 [SIMULAÇÃO] $message")
        }
    }

    private fun openBluetoothSettings() {
        try {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao abrir configurações", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}

@Composable
fun WearApp(
    greetingName: String,
    isTtsReady: Boolean,
    bluetoothConnected: Boolean,
    onSpeak: (String) -> Unit,
    onSendNotification: (String, String) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    toggleBluetoothSimulated: () -> Unit
) {
    var lastMessage by remember { mutableStateOf("Aguardando ação...") }
    val listState = rememberScalingLazyListState()

    DomaWearAssistTheme {
        AppScaffold {
            ScreenScaffold(scrollState = listState) {
                ScalingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(top = 40.dp, bottom = 40.dp, start = 8.dp, end = 8.dp)
                ) {
                    item { Text(text = "Olá, $greetingName!", fontSize = 16.sp) }
                    item { Text(text = if (isTtsReady) "TTS ATIVO ✅" else "TTS INICIANDO... ⚠", fontSize = 14.sp) }
                    item {
                        Text(
                            text = if (bluetoothConnected) "BT Conectado ✅" else "BT Desconectado ⚠",
                            fontSize = 14.sp
                        )
                    }

                    item {
                        Button(
                            onClick = {
                                val msg = "Botão A pressionado"
                                onSpeak(msg)
                                lastMessage = msg
                            },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) { Text("Botão A") }
                    }

                    item {
                        Button(
                            onClick = {
                                val msg = "Botão B pressionado"
                                onSpeak(msg)
                                lastMessage = msg
                            },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) { Text("Botão B") }
                    }

                    item {
                        Button(
                            onClick = {
                                val msg = "⚠ Alerta de segurança ativado!"
                                onSpeak(msg)
                                lastMessage = msg
                            },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) { Text("Alerta") }
                    }

                    item {
                        Button(
                            onClick = {
                                val msg = "📩 Nova notificação!"
                                onSpeak(msg)
                                onSendNotification("Doma Assist", msg)
                                lastMessage = "Notificação enviada!"
                            },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) { Text("Notificação") }
                    }

                    item {
                        Button(
                            onClick = onOpenBluetoothSettings,
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) { Text("Config. Bluetooth") }
                    }

                    item {
                        Button(
                            onClick = {
                                toggleBluetoothSimulated()
                                lastMessage = "BT simulado alterado"
                            },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) { Text("Simular BT") }
                    }

                    item {
                        Text(text = lastMessage, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
