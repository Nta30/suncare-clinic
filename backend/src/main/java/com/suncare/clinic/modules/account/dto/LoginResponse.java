package com.suncare.clinic.modules.account.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) { }
