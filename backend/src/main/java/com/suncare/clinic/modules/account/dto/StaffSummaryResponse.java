package com.suncare.clinic.modules.account.dto;

import java.util.List;

public record StaffSummaryResponse(long accountId, String staffCode, String fullName,
                                   String loginName, String status, List<String> roles) { }
