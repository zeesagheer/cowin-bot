package com.zeeshan.cowin.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScheduleRequest {
    private Integer center_id;
    private String session_id;
    private String slot;
    private String captcha;
    private Integer dose;
    private List<String> beneficiaries;
}
