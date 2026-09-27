package com.aicarchecking.ui.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aicarchecking.di.AppContainer
import com.aicarchecking.domain.model.Inspection
import com.aicarchecking.domain.model.Vehicle
import com.aicarchecking.navigation.Routes
import com.aicarchecking.ui.common.AppTopBar
import com.aicarchecking.ui.common.EmptyState
import com.aicarchecking.ui.common.appViewModel
import com.aicarchecking.ui.home.ListRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel(c: AppContainer) : ViewModel() {
    val items: StateFlow<List<Pair<Inspection, Vehicle?>>> = combine(
        c.inspectionRepository.observeAll(), c.vehicleRepository.observeAll(),
    ) { inspections, vehicles ->
        val byId = vehicles.associateBy { it.id }
        inspections.map { it to byId[it.vehicleId] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun HistoryScreen(navigate: (String) -> Unit) {
    val vm = appViewModel { c, _ -> HistoryViewModel(c) }
    val items by vm.items.collectAsStateWithLifecycle()
    val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    Scaffold(topBar = { AppTopBar("Inspection History") }) { padding ->
        if (items.isEmpty()) {
            EmptyState(Icons.Filled.History, "No inspections yet", "Your inspections, findings and reports are stored on this phone.", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
                items(items, key = { it.first.id }) { (inspection, vehicle) ->
                    ListRow(
                        "${vehicle?.displayName ?: "Vehicle"} — ${inspection.type.label}",
                        date.format(Date(inspection.startedAt)) + (if (inspection.isDemo) " · Demo" else "") +
                            " · " + inspection.status.name.lowercase().replace('_', ' '),
                    ) { navigate(Routes.inspection(inspection.id)) }
                }
            }
        }
    }
}
