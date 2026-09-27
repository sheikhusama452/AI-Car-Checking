package com.aicarchecking.ui.vehicle

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.aicarchecking.domain.model.Inspection
import com.aicarchecking.domain.model.Vehicle
import com.aicarchecking.domain.repository.InspectionRepository
import com.aicarchecking.domain.repository.VehicleRepository
import com.aicarchecking.media.storage.MediaStorageManager
import com.aicarchecking.navigation.Routes
import com.aicarchecking.ui.common.AppTopBar
import com.aicarchecking.ui.common.EmptyState
import com.aicarchecking.ui.common.SectionHeader
import com.aicarchecking.ui.common.appViewModel
import com.aicarchecking.ui.home.ListRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ---------- List ----------

class VehicleListViewModel(repo: VehicleRepository) : ViewModel() {
    val vehicles: StateFlow<List<Vehicle>> = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun VehicleListScreen(navigate: (String) -> Unit) {
    val vm = appViewModel { c, _ -> VehicleListViewModel(c.vehicleRepository) }
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AppTopBar("My Cars") },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { navigate(Routes.vehicleEdit()) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add car") })
        },
    ) { padding ->
        if (vehicles.isEmpty()) {
            EmptyState(Icons.Filled.DirectionsCar, "No cars yet", "Add the car you are inspecting. Every inspection is linked to a vehicle.", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
                items(vehicles, key = { it.id }) { v ->
                    ListRow(v.displayName, listOf(v.variant, v.registration, v.fuelType, if (v.isDemo) "Demo" else "").filter { it.isNotBlank() }.joinToString(" · ")) {
                        navigate(Routes.vehicleDetail(v.id))
                    }
                }
            }
        }
    }
}

// ---------- Edit ----------

data class VehicleForm(
    val make: String = "", val model: String = "", val variant: String = "", val year: String = "",
    val registration: String = "", val vin: String = "", val engineNumber: String = "", val engineType: String = "",
    val engineCapacity: String = "", val fuelType: String = "", val transmission: String = "", val mileage: String = "",
    val purchaseDate: String = "", val notes: String = "", val photoPath: String? = null,
)

class VehicleEditViewModel(
    private val repo: VehicleRepository,
    private val storage: MediaStorageManager,
    handle: SavedStateHandle,
) : ViewModel() {
    private val id: String? = handle.get<String>("id")
    private var original: Vehicle? = null
    private val _form = MutableStateFlow(VehicleForm())
    val form = _form.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val isEdit get() = id != null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    init {
        if (id != null) viewModelScope.launch {
            repo.get(id)?.let { v ->
                original = v
                _form.value = VehicleForm(
                    v.make, v.model, v.variant, v.year?.toString().orEmpty(), v.registration, v.vin, v.engineNumber,
                    v.engineType, v.engineCapacity, v.fuelType, v.transmission, v.currentMileageKm?.toString().orEmpty(),
                    v.purchaseDate?.let { dateFormat.format(Date(it)) }.orEmpty(), v.notes, v.photoPath,
                )
            }
        }
    }

    fun update(transform: (VehicleForm) -> VehicleForm) { _form.value = transform(_form.value) }

    fun setPhoto(uri: Uri) = viewModelScope.launch {
        runCatching { storage.importVehiclePhoto(uri) }
            .onSuccess { path -> update { it.copy(photoPath = path) } }
            .onFailure { _error.value = it.message ?: "Couldn't import the photo." }
    }

    fun save(onDone: (String) -> Unit) = viewModelScope.launch {
        val f = _form.value
        if (f.make.isBlank() || f.model.isBlank()) { _error.value = "Make and model are required."; return@launch }
        val year = f.year.takeIf { it.isNotBlank() }?.toIntOrNull()
        if (f.year.isNotBlank() && (year == null || year !in 1950..2100)) { _error.value = "Enter a valid year."; return@launch }
        val mileage = f.mileage.filter { it.isDigit() }.toLongOrNull()
        val purchase = f.purchaseDate.takeIf { it.isNotBlank() }?.let { runCatching { dateFormat.parse(it)?.time }.getOrNull() }
        if (f.purchaseDate.isNotBlank() && purchase == null) { _error.value = "Purchase date must be dd/mm/yyyy."; return@launch }
        val base = original ?: Vehicle(id = UUID.randomUUID().toString(), make = "", model = "")
        val v = base.copy(
            make = f.make.trim(), model = f.model.trim(), variant = f.variant.trim(), year = year,
            registration = f.registration.trim().uppercase(), vin = f.vin.trim().uppercase(), engineNumber = f.engineNumber.trim(),
            engineType = f.engineType.trim(), engineCapacity = f.engineCapacity.trim(), fuelType = f.fuelType.trim(),
            transmission = f.transmission.trim(), currentMileageKm = mileage, purchaseDate = purchase, notes = f.notes.trim(),
            photoPath = f.photoPath,
        )
        repo.save(v)
        onDone(v.id)
    }

    fun clearError() { _error.value = null }
}

@Composable
fun VehicleEditScreen(onBack: () -> Unit, onSaved: (String) -> Unit) {
    val vm = appViewModel { c, h -> VehicleEditViewModel(c.vehicleRepository, c.storage, h) }
    val form by vm.form.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setPhoto) }

    Scaffold(topBar = { AppTopBar(if (vm.isEdit) "Edit car" else "Add car", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            form.photoPath?.let {
                AsyncImage(File(it), "Vehicle photo", Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
            }
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (form.photoPath == null) "Add vehicle photo" else "Change photo")
            }
            Field("Make *", form.make) { v -> vm.update { it.copy(make = v) } }
            Field("Model *", form.model) { v -> vm.update { it.copy(model = v) } }
            Field("Variant", form.variant) { v -> vm.update { it.copy(variant = v) } }
            Field("Year", form.year, KeyboardType.Number) { v -> vm.update { it.copy(year = v.take(4)) } }
            Field("Registration", form.registration) { v -> vm.update { it.copy(registration = v) } }
            Field("VIN / Chassis number", form.vin) { v -> vm.update { it.copy(vin = v) } }
            Field("Engine number (if available)", form.engineNumber) { v -> vm.update { it.copy(engineNumber = v) } }
            Field("Engine type", form.engineType) { v -> vm.update { it.copy(engineType = v) } }
            Field("Engine capacity", form.engineCapacity) { v -> vm.update { it.copy(engineCapacity = v) } }
            Field("Fuel type", form.fuelType) { v -> vm.update { it.copy(fuelType = v) } }
            Field("Transmission", form.transmission) { v -> vm.update { it.copy(transmission = v) } }
            Field("Current mileage (km)", form.mileage, KeyboardType.Number) { v -> vm.update { it.copy(mileage = v) } }
            Field("Purchase date (dd/mm/yyyy)", form.purchaseDate) { v -> vm.update { it.copy(purchaseDate = v) } }
            Field("Notes", form.notes, singleLine = false) { v -> vm.update { it.copy(notes = v) } }
            Button(onClick = { vm.save(onSaved) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save car") }
            Spacer(Modifier.height(24.dp))
        }
    }
    error?.let {
        AlertDialog(onDismissRequest = vm::clearError, confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } }, text = { Text(it) })
    }
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth(),
    )
}

// ---------- Detail ----------

class VehicleDetailViewModel(
    private val vehicles: VehicleRepository,
    inspections: InspectionRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val id: String = checkNotNull(handle["vehicleId"])
    val vehicle: StateFlow<Vehicle?> = vehicles.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val inspections: StateFlow<List<Inspection>> = inspections.observeForVehicle(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(onDone: () -> Unit) = viewModelScope.launch { vehicles.delete(id); onDone() }
}

@Composable
fun VehicleDetailScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = appViewModel { c, h -> VehicleDetailViewModel(c.vehicleRepository, c.inspectionRepository, h) }
    val vehicle by vm.vehicle.collectAsStateWithLifecycle()
    val inspections by vm.inspections.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Scaffold(topBar = {
        AppTopBar(vehicle?.displayName ?: "Car", onBack) {
            IconButton(onClick = { navigate(Routes.vehicleEdit(vm.id)) }) { Icon(Icons.Filled.Edit, "Edit") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }) { padding ->
        val v = vehicle ?: return@Scaffold
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                v.photoPath?.let {
                    AsyncImage(File(it), "Vehicle photo", Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(12.dp))
                }
                val rows = listOf(
                    "Variant" to v.variant, "Year" to (v.year?.toString() ?: ""), "Registration" to v.registration,
                    "VIN / Chassis" to v.vin, "Engine number" to v.engineNumber, "Engine" to listOf(v.engineType, v.engineCapacity).filter { it.isNotBlank() }.joinToString(" "),
                    "Fuel" to v.fuelType, "Transmission" to v.transmission,
                    "Mileage" to (v.currentMileageKm?.let { "%,d km".format(it) } ?: ""),
                    "Purchase date" to (v.purchaseDate?.let { date.format(Date(it)) } ?: ""), "Notes" to v.notes,
                ).filter { it.second.isNotBlank() }
                rows.forEach { (k, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(k, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { navigate(Routes.inspect()) }, modifier = Modifier.fillMaxWidth()) { Text("Start new inspection") }
                SectionHeader("Inspections (Digital Car Passport)")
                if (inspections.isEmpty()) Text("No inspections yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(inspections, key = { it.id }) { i ->
                ListRow(i.type.label, date.format(Date(i.startedAt)) + " · " + i.status.name.lowercase().replace('_', ' ')) {
                    navigate(Routes.inspection(i.id))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this car?") },
            text = { Text("This permanently deletes the car, all its inspections, findings and evidence files from this phone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
