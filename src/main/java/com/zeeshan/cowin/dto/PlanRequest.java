package com.zeeshan.cowin.dto;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class PlanRequest {
    private String pinCode;
    private String districtId;
    private boolean onlyFree;
    private Boolean dose1;
    private boolean eighteenPlusOnly;
    private Set<String> vaccineList = new HashSet<>();
    private Set<String> skipSessions = new HashSet<>();
}
