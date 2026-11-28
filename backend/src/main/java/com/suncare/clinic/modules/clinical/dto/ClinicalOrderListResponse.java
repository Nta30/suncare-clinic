package com.suncare.clinic.modules.clinical.dto;

public record ClinicalOrderListResponse(long id,String code,long serviceId,String serviceName,String indication,
        long performingDepartmentId,String departmentName,Long requestedRoomId,String roomName,String status,
        String createdAt,String fullyPaidAt,Integer validityDays,String expiresAt,Long invoiceId,
        String invoiceCode,String invoiceStatus,String lineStatus,String amount,String paymentStatus,
        String serviceCode,String paidAmount,String outstandingAmount) {}
