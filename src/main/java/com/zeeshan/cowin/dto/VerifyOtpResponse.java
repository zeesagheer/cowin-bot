package com.zeeshan.cowin.dto;

import lombok.Data;

@Data
public class VerifyOtpResponse {
    private String token;
    private String isNewAccount;
}
