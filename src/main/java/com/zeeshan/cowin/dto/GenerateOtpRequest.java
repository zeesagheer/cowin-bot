package com.zeeshan.cowin.dto;

import lombok.Data;

@Data
public class GenerateOtpRequest {
    private String secret;
    private String mobile;
}
