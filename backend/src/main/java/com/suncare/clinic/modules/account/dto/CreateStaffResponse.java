package com.suncare.clinic.modules.account.dto;

import java.util.List;

public record CreateStaffResponse(long accountId, String staffCode, String loginName,
                                  List<String> roles) { }
