package com.suncare.clinic.modules.organization.dto;

public record DepartmentResponse(long id, String code, String name, String departmentType,
                                 boolean isActive, String version) { }
