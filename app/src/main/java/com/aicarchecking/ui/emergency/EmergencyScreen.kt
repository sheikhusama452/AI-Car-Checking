package com.aicarchecking.ui.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aicarchecking.safety.SafetyHazard
import com.aicarchecking.ui.common.AppTopBar
import com.aicarchecking.ui.common.IntegrationRequiredBanner
import com.aicarchecking.ui.common.WarningBanner
import com.aicarchecking.ui.theme.Brand

private data class Question(val id: String, val text: String, val yesHazard: SafetyHazard? = null, val noHazard: SafetyHazard? = null)

private val questions = listOf(
    Question("injured", "Is anyone injured?", yesHazard = SafetyHazard.INJURY),
    Question("fire", "Is there smoke from the engine/cabin, flames or a burning smell?", yesHazard = SafetyHazard.FIRE_SMOKE),
    Question("fuel", "Do you smell fuel or see fuel leaking?", yesHazard = SafetyHazard.FUEL_LEAK),
    Question("accident", "Was there an accident?", yesHazard = SafetyHazard.SEVERE_ACCIDENT_DAMAGE),
    Question("traffic", "Is the vehicle blocking traffic?"),
    Question("brakes", "Are the brakes not working properly?", yesHazard = SafetyHazard.BRAKE_FAILURE),
    Question("overheat", "Is the temperature gauge high/red or is steam visible?", yesHazard = SafetyHazard.OVERHEATING),
    Question("oil", "Is the red oil-pressure light on?", yesHazard = SafetyHazard.OIL_PRESSURE),
    Question("warning", "Any other warning lights?"),
    Question("start", "Can the vehicle start?"),
    Question("leak", "Any other fluid leak under the car?"),
    Question("sound", "Any strange sound?"),
)

/** Deterministic, offline safety triage — works without internet or an AI provider. */
@Composable
fun EmergencyScreen(onBack: () -> Unit) {
    val answers = remember { mutableStateMapOf<String, Boolean>() }
    val context = LocalContext.current
    val hazards = questions.mapNotNull { q ->
        when (answers[q.id]) {
            true -> q.yesHazard
            false -> q.noHazard
            null -> null
        }
    }.distinct().sortedByDescending { it.minSeverity.rank }

    Scaffold(topBar = { AppTopBar("Car Broken Down?", onBack) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                WarningBanner("First: if it is safe, turn on hazard lights and move yourself and passengers away from traffic.")
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1122"))) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Red, contentColor = Color.White),
                ) { Text("Call Rescue 1122 (Pakistan)") }
                Text("Outside Pakistan, dial your local emergency number.", style = MaterialTheme.typography.bodySmall)
            }
            if (hazards.isNotEmpty()) {
                items(hazards) { h -> WarningBanner("${h.title}: ${h.guidance}") }
            }
            items(questions, key = { it.id }) { q ->
                Column {
                    Text(q.text, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = answers[q.id] == true, onClick = { answers[q.id] = true }, label = { Text("Yes") })
                        FilterChip(selected = answers[q.id] == false, onClick = { answers[q.id] = false }, label = { Text("No") })
                    }
                }
            }
            item {
                if (answers["start"] == false && hazards.isEmpty()) {
                    Text(
                        "If the car won't start and there is no safety risk: keep hazard lights on, stay in a safe place and contact a mobile mechanic or towing service. Do not attempt electrical or fuel-system repairs at the roadside.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (answers["traffic"] == true) {
                    Text("If the car blocks traffic, place a warning triangle well behind it if you can do so safely, and call traffic police (15 in Pakistan).", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                IntegrationRequiredBanner("Nearby mechanics & towing", "Location-based mechanic and towing listings are planned for a later phase. No listings are shown until a verified data source is connected.")
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
