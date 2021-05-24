package com.zeeshan.cowin.dto;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class PlanRequest {
    private String token;
    private String pinCode;
    private String districtId;
    private Set<String> skipSessions = new HashSet<>();
    private Set<String> pinCodesInDistrict = new HashSet<>();
}
