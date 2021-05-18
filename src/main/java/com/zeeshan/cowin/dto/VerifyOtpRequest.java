package com.zeeshan.cowin.dto;

import lombok.Data;

@Data
public class VerifyOtpRequest {
    private String otp;
    private String txnId;
}
