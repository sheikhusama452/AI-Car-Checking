package com.aicarchecking.navigation

import com.aicarchecking.domain.model.CaptureMode
import com.aicarchecking.domain.model.InspectionStep

object Routes {
    const val HOME = "home"
    const val CARS = "cars"
    const val INSPECT = "inspect?step={step}"
    const val MECHANIC = "mechanic"
    const val HISTORY = "history"

    const val VEHICLE_EDIT = "vehicle/edit?id={id}"
    const val VEHICLE_DETAIL = "vehicle/{vehicleId}"
    const val INSPECTION = "inspection/{inspectionId}"
    const val STEP = "inspection/{inspectionId}/step/{step}"
    const val CAMERA = "camera/{inspectionId}/{step}/{mode}"
    const val PAINT = "inspection/{inspectionId}/paint"
    const val MILEAGE = "inspection/{inspectionId}/mileage"
    const val OBD = "obd?inspectionId={inspectionId}"
    const val REPORT = "inspection/{inspectionId}/report"
    const val EMERGENCY = "emergency"
    const val SETTINGS = "settings"
    const val AI_PROVIDER = "settings/ai"
    const val STORAGE = "settings/storage"
    const val PRIVACY = "settings/privacy"
    const val ABOUT = "settings/about"

    fun inspect(step: InspectionStep? = null) = if (step == null) "inspect" else "inspect?step=${step.name}"
    fun vehicleEdit(id: String? = null) = if (id == null) "vehicle/edit" else "vehicle/edit?id=$id"
    fun vehicleDetail(id: String) = "vehicle/$id"
    fun inspection(id: String) = "inspection/$id"
    fun step(inspectionId: String, step: InspectionStep) = "inspection/$inspectionId/step/${step.name}"
    fun camera(inspectionId: String, step: InspectionStep, mode: CaptureMode) = "camera/$inspectionId/${step.name}/${mode.name}"
    fun paint(inspectionId: String) = "inspection/$inspectionId/paint"
    fun mileage(inspectionId: String) = "inspection/$inspectionId/mileage"
    fun obd(inspectionId: String? = null) = if (inspectionId == null) "obd" else "obd?inspectionId=$inspectionId"
    fun report(inspectionId: String) = "inspection/$inspectionId/report"
}
