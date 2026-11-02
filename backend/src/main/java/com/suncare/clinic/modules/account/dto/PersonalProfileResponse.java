package com.suncare.clinic.modules.account.dto;

import java.time.LocalDate;

public record PersonalProfileResponse(String accountType, String code, String fullName,
                                      LocalDate birthDate, String sex, String phone, String email,
                                      String address, String identityNumber, String emergencyName,
                                      String emergencyPhone, String professionalLicense,
                                      String qualification, String version) { }
