package com.aicarchecking.data.obd

import kotlinx.coroutines.flow.Flow

/**
 * OBD-II adapter abstraction. Transport implementations (Bluetooth Classic SPP for ELM327
 * clones, BLE GATT for BLE adapters) are Phase 3 and marked "Integration Required" in the UI.
 * The command/response decoding below is transport-independent and already testable.
 */
interface ObdAdapter {
    val transport: ObdTransport
    val connectionState: Flow<ObdConnectionState>
    suspend fun connect(deviceAddress: String)
    suspend fun disconnect()
    /** Sends a raw ELM327 command (e.g. "010C") and returns the raw response text. */
    suspend fun send(command: String): String
}

enum class ObdTransport { BLUETOOTH_CLASSIC, BLUETOOTH_LE }

sealed interface ObdConnectionState {
    data object Disconnected : ObdConnectionState
    data object Connecting : ObdConnectionState
    data class Connected(val protocol: String?) : ObdConnectionState
    data class Failed(val userMessage: String) : ObdConnectionState
}

/** Standard mode-01 PIDs used by the live-data screen. Future modules (ABS/SRS/TCM) extend this. */
enum class ObdPid(val command: String, val label: String, val unit: String) {
    ENGINE_RPM("010C", "Engine RPM", "rpm"),
    VEHICLE_SPEED("010D", "Vehicle speed", "km/h"),
    COOLANT_TEMP("0105", "Coolant temperature", "°C"),
    THROTTLE_POSITION("0111", "Throttle position", "%"),
    SHORT_FUEL_TRIM_B1("0106", "Short-term fuel trim (B1)", "%"),
    LONG_FUEL_TRIM_B1("0107", "Long-term fuel trim (B1)", "%"),
    MAF_RATE("0110", "MAF air flow", "g/s"),
    INTAKE_MAP("010B", "Intake manifold pressure", "kPa"),
    CONTROL_MODULE_VOLTAGE("0142", "Control module voltage", "V"),
}

object ObdPidDecoder {
    /**
     * Decodes an ELM327 mode-01 response such as "41 0C 1A F8". Returns null when the response
     * does not match the requested PID or reports NO DATA.
     */
    fun decode(pid: ObdPid, response: String): Double? {
        val bytes = response.uppercase()
            .replace(">", " ")
            .split(Regex("\\s+"))
            .filter { it.length == 2 && it.all { c -> c.isDigit() || c in 'A'..'F' } }
            .map { it.toInt(16) }
        val pidByte = pid.command.substring(2).toInt(16)
        val start = bytes.windowed(2).indexOfFirst { it[0] == 0x41 && it[1] == pidByte }
        if (start < 0) return null
        val data = bytes.drop(start + 2)
        val a = data.getOrNull(0) ?: return null
        val b = data.getOrNull(1)
        return when (pid) {
            ObdPid.ENGINE_RPM -> b?.let { (256.0 * a + it) / 4.0 }
            ObdPid.VEHICLE_SPEED -> a.toDouble()
            ObdPid.COOLANT_TEMP -> a - 40.0
            ObdPid.THROTTLE_POSITION -> a * 100.0 / 255.0
            ObdPid.SHORT_FUEL_TRIM_B1, ObdPid.LONG_FUEL_TRIM_B1 -> (a - 128) * 100.0 / 128.0
            ObdPid.MAF_RATE -> b?.let { (256.0 * a + it) / 100.0 }
            ObdPid.INTAKE_MAP -> a.toDouble()
            ObdPid.CONTROL_MODULE_VOLTAGE -> b?.let { (256.0 * a + it) / 1000.0 }
        }
    }

    /** Decodes a mode-03 response ("43 01 33 00 00 00 00") into DTC strings like "P0133". */
    fun decodeDtcs(response: String): List<String> {
        val bytes = response.uppercase().split(Regex("\\s+"))
            .filter { it.length == 2 && it.all { c -> c.isDigit() || c in 'A'..'F' } }
            .map { it.toInt(16) }
        val start = bytes.indexOf(0x43)
        if (start < 0) return emptyList()
        return bytes.drop(start + 1).chunked(2).filter { it.size == 2 && (it[0] != 0 || it[1] != 0) }.map { (a, b) ->
            val system = when (a shr 6) { 0 -> 'P'; 1 -> 'C'; 2 -> 'B'; else -> 'U' }
            val d1 = (a shr 4) and 0x3
            val d2 = a and 0xF
            "%c%d%X%02X".format(system, d1, d2, b)
        }
    }
}

data class DtcInfo(
    val code: String,
    val meaning: String,
    val possibleCauses: List<String>,
    val severityHint: String,
    val recommendation: String,
)

/**
 * Small local dictionary of common generic (SAE) powertrain codes. Manufacturer-specific codes are
 * reported as unknown rather than guessed.
 */
object DtcDictionary {
    private val entries = listOf(
        DtcInfo(
            "P0420", "Catalyst system efficiency below threshold (Bank 1)",
            listOf("Catalytic converter issue", "Oxygen sensor issue", "Exhaust leak", "Other related engine conditions"),
            "MEDIUM", "Diagnose before replacing parts. Do not assume the catalytic converter must be replaced.",
        ),
        DtcInfo(
            "P0300", "Random/multiple cylinder misfire detected",
            listOf("Ignition system issue", "Fuel delivery issue", "Vacuum leak", "Mechanical engine condition"),
            "HIGH", "Avoid heavy load. A flashing check-engine light means stop driving and seek diagnosis.",
        ),
        DtcInfo(
            "P0171", "System too lean (Bank 1)",
            listOf("Vacuum/intake leak", "MAF sensor issue", "Fuel pressure issue", "Exhaust leak before O2 sensor"),
            "MEDIUM", "Diagnostic testing recommended (smoke test, fuel pressure, sensor data).",
        ),
        DtcInfo(
            "P0172", "System too rich (Bank 1)",
            listOf("MAF sensor issue", "Leaking injector", "Fuel pressure issue", "Oxygen sensor issue"),
            "MEDIUM", "Diagnostic testing recommended.",
        ),
        DtcInfo(
            "P0128", "Coolant thermostat (coolant temperature below regulating temperature)",
            listOf("Thermostat stuck open", "Coolant temperature sensor issue", "Wiring issue"),
            "LOW", "Have the cooling system checked.",
        ),
        DtcInfo(
            "P0133", "O2 sensor circuit slow response (Bank 1, Sensor 1)",
            listOf("Oxygen sensor ageing", "Exhaust leak", "Wiring issue"),
            "LOW", "Diagnostic testing recommended.",
        ),
        DtcInfo(
            "P0442", "Evaporative emission system leak detected (small leak)",
            listOf("Loose or faulty fuel cap", "EVAP hose leak", "Purge/vent valve issue"),
            "LOW", "Check the fuel cap first, then have the EVAP system tested.",
        ),
        DtcInfo(
            "P0500", "Vehicle speed sensor malfunction",
            listOf("Speed sensor issue", "Wiring issue", "Module issue"),
            "MEDIUM", "Speed-dependent systems (ABS, transmission) may be affected. Diagnose soon.",
        ),
        DtcInfo(
            "P0700", "Transmission control system malfunction",
            listOf("Transmission control module has stored its own code", "Transmission electrical issue"),
            "HIGH", "Read transmission module codes with a capable scanner. Transmission specialist recommended.",
        ),
    ).associateBy { it.code }

    fun lookup(code: String): DtcInfo? = entries[code.trim().uppercase()]

    fun isValidFormat(code: String): Boolean = Regex("^[PCBU][0-3][0-9A-F]{3}$").matches(code.trim().uppercase())
}
