package com.example.suncareclinic

import com.example.suncareclinic.data.api.ApiClient
import com.example.suncareclinic.data.dto.EncounterCompleteRequestDto
import com.example.suncareclinic.data.dto.EncounterCompleteResponseDto
import com.example.suncareclinic.data.dto.EncounterHistoryResponseDto
import com.example.suncareclinic.data.dto.EncounterWorkItemDto
import com.example.suncareclinic.data.repository.confirmsCompletionOf
import com.example.suncareclinic.ui.screens.encounter.DoctorEncountersUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterCompletionTest {
    @Test
    fun sendsConfirmationAndValidatesResponseIncludingReplay() {
        val gson = ApiClient.gson
        assertEquals("{\"completionConfirmed\":true}", gson.toJson(EncounterCompleteRequestDto()))
        val replay = gson.fromJson(
            """{"encounterId":42,"status":"COMPLETED","completedAt":"2026-11-27T08:15:00Z","replayed":true}""",
            EncounterCompleteResponseDto::class.java,
        )
        assertTrue(replay.replayed)
        assertTrue(replay.confirmsCompletionOf(42))
        assertFalse(replay.confirmsCompletionOf(43))
        assertFalse(replay.copy(completedAt = null).confirmsCompletionOf(42))
        assertFalse(replay.copy(completedAt = "invalid").confirmsCompletionOf(42))
        assertFalse(replay.copy(status = "IN_PROGRESS").confirmsCompletionOf(42))
    }

    @Test
    fun onlyOffersCompletionWhileAnEditableEncounterIsInProgress() {
        val encounter = EncounterWorkItemDto(encounterId = 42, encounterCode = "ENC-42", status = "IN_PROGRESS")
        val detail = EncounterHistoryResponseDto(encounter = encounter, editable = true)
        val state = DoctorEncountersUiState(detail = detail)
        assertTrue(state.canCompleteEncounter)
        assertFalse(state.copy(detail = detail.copy(editable = false)).canCompleteEncounter)
        assertFalse(state.copy(detail = detail.copy(encounter = encounter.copy(status = "COMPLETED"))).canCompleteEncounter)
    }
}
