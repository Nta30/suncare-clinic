package com.suncare.clinic.modules.account.dto;

import java.util.List;
import java.time.LocalDate;

public record StaffSummaryResponse(long accountId, long staffId, String staffCode, String fullName,
                                   String loginName, String status, List<String> roles,
                                   LocalDate birthDate, String sex, String phone, String email,
                                   String address, String professionalLicense, String qualification,
                                   List<String> departments, List<String> specialties, String version) { }
