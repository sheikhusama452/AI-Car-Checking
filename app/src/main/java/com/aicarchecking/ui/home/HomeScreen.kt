package com.aicarchecking.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aicarchecking.data.demo.DemoDataSeeder
import com.aicarchecking.data.settings.AppSettings
import com.aicarchecking.data.settings.SettingsRepository
import com.aicarchecking.domain.model.Finding
import com.aicarchecking.domain.model.Inspection
import com.aicarchecking.domain.model.InspectionStep
import com.aicarchecking.domain.model.Vehicle
import com.aicarchecking.domain.repository.FindingRepository
import com.aicarchecking.domain.repository.InspectionRepository
import com.aicarchecking.domain.repository.VehicleRepository
import com.aicarchecking.navigation.Routes
import com.aicarchecking.ui.common.FindingCard
import com.aicarchecking.ui.common.Pill
import com.aicarchecking.ui.common.SectionHeader
import com.aicarchecking.ui.common.appViewModel
import com.aicarchecking.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeViewModel(
    vehicles: VehicleRepository,
    inspections: InspectionRepository,
    findings: FindingRepository,
    settings: SettingsRepository,
    private val seeder: DemoDataSeeder,
) : ViewModel() {
    val vehicles: StateFlow<List<Vehicle>> = vehicles.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recent: StateFlow<List<Inspection>> = inspections.observeAll().map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val important: StateFlow<List<Finding>> = findings.observeImportantUnresolved(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _demoBusy = MutableStateFlow(false)
    val demoBusy = _demoBusy.asStateFlow()

    fun openDemo(onReady: (String) -> Unit) {
        if (_demoBusy.value) return
        viewModelScope.launch {
            _demoBusy.value = true
            val existing = recent.value.firstOrNull { it.isDemo }
            val id = if (existing != null && settings.value.demoSeeded) existing.id else seeder.seed()
            _demoBusy.value = false
            onReady(id)
        }
    }
}

@Composable
fun HomeScreen(navigate: (String) -> Unit) {
    val vm = appViewModel { c, _ -> HomeViewModel(c.vehicleRepository, c.inspectionRepository, c.findingRepository, c.settings, c.demoSeeder) }
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val important by vm.important.collectAsStateWithLifecycle()
    val demoBusy by vm.demoBusy.collectAsStateWithLifecycle()
    val vehicleNames = vehicles.associate { it.id to it.displayName }
    val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI Car Checking", style = MaterialTheme.typography.headlineLarge)
                    Text("Painted or Original? Check Before You Buy.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = { navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, "Settings") }
            }
            Spacer(Modifier.height(14.dp))
            HeroCard(onStart = { navigate(Routes.inspect()) })
        }

        item {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction(Icons.Filled.SupportAgent, "Ask AI Mechanic", Modifier.weight(1f)) { navigate(Routes.MECHANIC) }
                SecondaryAction(Icons.Filled.Bluetooth, "Scan OBD", Modifier.weight(1f)) { navigate(Routes.obd()) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction(Icons.Filled.DirectionsCar, "My Cars", Modifier.weight(1f)) { navigate(Routes.CARS) }
                SecondaryAction(Icons.Filled.History, "Inspection History", Modifier.weight(1f)) { navigate(Routes.HISTORY) }
            }
        }

        item {
            SectionHeader("Quick Inspection")
            val quick = listOf(
                Triple(Icons.Filled.CameraAlt, "Take Car Photos", InspectionStep.FRONT_LEFT_FENDER),
                Triple(Icons.Filled.GraphicEq, "Record Engine", InspectionStep.ENGINE_SOUND),
                Triple(Icons.Filled.Speed, "Check Dashboard", InspectionStep.DASHBOARD),
                Triple(Icons.Filled.Cloud, "Check Smoke", InspectionStep.EXHAUST_SMOKE),
                Triple(Icons.Filled.Bluetooth, "Scan OBD", InspectionStep.OBD),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(quick) { (icon, label, step) ->
                    QuickTile(icon, label) { navigate(Routes.inspect(step)) }
                }
            }
        }

        item {
            SectionHeader("Demo Mode")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Science, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("See a complete sample inspection", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Works fully offline: Painted or Original, Mileage Check, Engine, Smoke, OBD and the AI Report — using clearly labelled sample data.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.openDemo { navigate(Routes.inspection(it)) } }, enabled = !demoBusy) {
                        if (demoBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Preparing demo…")
                        } else Text("Open Demo Inspection")
                    }
                }
            }
        }

        item {
            SectionHeader("My Cars") { TextButton(onClick = { navigate(Routes.CARS) }) { Text("See all") } }
            if (vehicles.isEmpty()) {
                OutlinedButton(onClick = { navigate(Routes.vehicleEdit()) }, modifier = Modifier.fillMaxWidth()) { Text("Add your first car") }
            }
        }
        items(vehicles.take(3), key = { "v_" + it.id }) { v ->
            ListRow(v.displayName, listOfNotNull(v.variant.ifBlank { null }, v.registration.ifBlank { null }, if (v.isDemo) "Demo" else null).joinToString(" · ")) {
                navigate(Routes.vehicleDetail(v.id))
            }
        }

        item { SectionHeader("Recent Inspections") }
        if (recent.isEmpty()) item {
            Text("No inspections yet. Start one to build your car's evidence history.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(recent, key = { "i_" + it.id }) { i ->
            ListRow(
                "${vehicleNames[i.vehicleId] ?: "Vehicle"} — ${i.type.label}",
                date.format(Date(i.startedAt)) + (if (i.isDemo) " · Demo" else "") + " · " + i.status.name.replace('_', ' ').lowercase(),
            ) { navigate(Routes.inspection(i.id)) }
        }

        if (important.isNotEmpty()) {
            item { SectionHeader("Important Findings") }
            items(important, key = { "f_" + it.id }) { f ->
                FindingCard(f, Modifier.padding(bottom = 8.dp)) { navigate(Routes.inspection(f.inspectionId)) }
            }
        }

        item {
            SectionHeader("Emergency")
            Button(
                onClick = { navigate(Routes.EMERGENCY) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Red, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Warning, null)
                Spacer(Modifier.width(10.dp))
                Text("CAR BROKEN DOWN?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeroCard(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Color(0xFF003F55), Color(0xFF0B1220), Color(0xFF1B2640))))
            .padding(20.dp),
    ) {
        Pill("AI-powered vehicle inspection assistant", Brand.Cyan)
        Spacer(Modifier.height(12.dp))
        Text("See the evidence. Understand the risks. Know what to check next.", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand.Amber, contentColor = Brand.Navy),
        ) {
            Icon(Icons.Filled.CameraAlt, null)
            Spacer(Modifier.width(10.dp))
            Text("START CAR INSPECTION", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SecondaryAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(Modifier.width(112.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ListRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
