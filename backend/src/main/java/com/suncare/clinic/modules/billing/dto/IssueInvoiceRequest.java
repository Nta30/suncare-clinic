package com.suncare.clinic.modules.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record IssueInvoiceRequest(@NotBlank String version) {}
