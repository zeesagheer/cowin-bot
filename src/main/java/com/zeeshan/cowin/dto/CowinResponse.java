package com.zeeshan.cowin.dto;

import lombok.Data;

import java.util.List;

@Data
public class CowinResponse {
    private List<Centers> centers;

    @Data
    static public class Centers {
        private Long center_id;
        private String name;
        private String address;
        private String pincode;
        private String fee_type; //Paid,Free
        private List<Session> sessions;
        private List<Vaccine> vaccine_fees;

        @Data
        static public class Session {
            private String session_id;
            private String date;
            private int available_capacity;
            private int available_capacity_dose1;
            private int available_capacity_dose2;
            private int min_age_limit;
            private List<String> slots;
            private String vaccine; //COVAXIN
        }

        @Data
        static public class Vaccine {
            private String vaccine; //COVAXIN
            private String fee;
        }
    }
}
