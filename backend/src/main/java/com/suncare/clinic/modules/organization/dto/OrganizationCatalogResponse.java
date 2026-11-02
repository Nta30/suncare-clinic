package com.suncare.clinic.modules.organization.dto;

import java.util.List;

public record OrganizationCatalogResponse(List<DepartmentResponse> departments,
                                          List<SpecialtyResponse> specialties,
                                          List<DepartmentSpecialtyResponse> links) { }
