package com.suncare.clinic.modules.clinical.dto;

import java.math.BigDecimal;

public record ClinicalOrderResponse(long id, String code, long encounterId, long serviceId,
        long performingDepartmentId, Long requestedRoomId, String indication, String status,
        long invoiceId, long invoiceLineId, long priceId, String serviceName, BigDecimal amount,
        boolean replayed) { }
