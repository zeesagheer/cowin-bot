package com.zeeshan.cowin.dto;

import lombok.Data;

import java.util.List;

@Data
public class BeneficiariesResponse {
    private List<Beneficiary> beneficiaries;

    @Data
    static public class Beneficiary {
        private String beneficiary_reference_id;
        private String name;
        private String birth_year;
        private String vaccine;
        private String dose1_date;
        private String dose2_date;
        private String vaccination_status;
    }
}
