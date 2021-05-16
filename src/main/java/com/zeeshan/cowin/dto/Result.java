package com.zeeshan.cowin.dto;

import lombok.Data;

@Data
public class Result {
    private Long centerId;
    private String name;
    private String address;
    private String fees;
    private Integer age;
    private String date;
    private Integer slots;
    private Integer dose1;
    private Integer dose2;
    private String vaccine;
    private String pinCode;
    private String sessionId;

}
