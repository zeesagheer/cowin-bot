package com.zeeshan.cowin.dto;

import lombok.Data;

@Data
public class ScheduleResponse {
    private String errorCode;
    private String error;
    private String appointment_id;
}
