package com.suncare.clinic.modules.account.dto;

import java.util.List;

public record CurrentAccountResponse(long accountId, String loginName, String accountType,
                                     Long patientId, Long staffId, List<String> roles,
                                     List<String> permissions) { }
