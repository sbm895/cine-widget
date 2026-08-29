package com.cinewidget

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.cinewidget.worker.ScheduleUpdateWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar actualización periódica de WorkManager (cada 30 min)
        setupPeriodicSync(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConfigScreen(this)
                }
            }
        }
    }

    private fun setupPeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<ScheduleUpdateWorker>(
            30, TimeUnit.MINUTES,
            10, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "cinema_schedule_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }
}

@Composable
fun ConfigScreen(context: Context) {
    val prefs = remember { context.getSharedPreferences("cine_widget_prefs", Context.MODE_PRIVATE) }
    var backendUrl by remember {
        mutableStateOf(prefs.getString("backend_url", "https://tu-backend-en-google-cloud.a.run.app/") ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🎬 Cine Widget Config",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ingresa la URL pública de tu API en Google Cloud Run / Render / Túnel Local:",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = backendUrl,
            onValueChange = { backendUrl = it },
            label = { Text("Backend Base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                prefs.edit().putString("backend_url", backendUrl.trim()).apply()
                // Disparar sincronización manual
                val request = OneTimeWorkRequestBuilder<ScheduleUpdateWorker>().build()
                WorkManager.getInstance(context).enqueue(request)
                Toast.makeText(context, "URL guardada y sincronización iniciada", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar y Sincronizar Ahora")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ℹ️ Instrucciones:",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Añade el widget de Cartelera a tu pantalla de inicio.\n2. Al tocar una función se abrirá la compra en el navegador.\n3. Los datos se actualizan automáticamente en segundo plano.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
