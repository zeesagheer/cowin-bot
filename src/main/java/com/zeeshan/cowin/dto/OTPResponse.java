package com.zeeshan.cowin.dto;

import lombok.Data;

@Data
public class OTPResponse {
    private String otp;
    private Long time;
    private Long chatId;
}
