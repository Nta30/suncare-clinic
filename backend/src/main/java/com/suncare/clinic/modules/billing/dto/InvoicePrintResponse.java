package com.suncare.clinic.modules.billing.dto;

import java.util.List;

/** Financial snapshot and current operational guidance are deliberately separate. */
public record InvoicePrintResponse(InvoiceResponse invoice, String patientCode, String encounterCode,
                                   List<RoomGuidance> rooms) {
    public record RoomGuidance(long lineId, String roomCode, String roomName, String source) {}
}
