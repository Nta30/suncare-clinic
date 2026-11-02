package com.suncare.clinic.modules.account.dto;

import java.time.LocalDate;
import java.util.List;

public record PersonalProfileResponse(String accountType, String code, String fullName,
                                      LocalDate birthDate, String sex, String phone, String email,
                                      String address, String identityNumber, String emergencyName,
                                      String emergencyPhone, String professionalLicense,
                                      String qualification, List<String> departments,
                                      List<String> specialties, String version) { }
