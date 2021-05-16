package com.zeeshan.cowin.dto;

import lombok.Data;

@Data
public class CowinRequest {
    private String districtId;
    private String pinCode;
    private String date;
}
