package com.example.suncareclinic

import com.example.suncareclinic.data.api.ApiClient
import com.example.suncareclinic.data.dto.PrescriptionResponseDto
import com.example.suncareclinic.ui.screens.encounter.displayDose
import com.example.suncareclinic.ui.screens.encounter.displayQuantity
import org.junit.Assert.assertEquals
import org.junit.Test

class PrescriptionDisplayTest {
    private val prescription = ApiClient.gson.fromJson(
        """
        {
          "id": 1, "code": "RX-001", "status": "ISSUED", "issuedAt": "2026-11-27T01:00:00Z",
          "items": [
            {"id": 2, "lineNo": 1, "drugName": "Thuốc thử", "unit": "viên", "dose": "2.5",
             "route": "Uống", "frequency": "2 lần/ngày", "durationDays": 5, "quantity": 1.250,
             "doseUnit": "mL", "dispensingUnit": "chai"}
          ]
        }
        """.trimIndent(),
        PrescriptionResponseDto::class.java,
    )

    @Test
    fun displaysSeparateUnitsFromPrescriptionSnapshot() {
        val item = prescription.items.single()
        assertEquals("2,5 mL", item.displayDose())
        assertEquals("1,25 chai", item.displayQuantity())
    }

    @Test
    fun preservesLegacyDoseAndFallsBackToLegacyQuantityUnit() {
        val item = prescription.items.single().copy(dose = "2,5 mL", dispensingUnit = null)
        assertEquals("2,5 mL", item.displayDose())
        assertEquals("1,25 viên", item.displayQuantity())
        assertEquals("2.5", item.copy(dose = "2.5", doseUnit = null).displayDose())
    }
}
