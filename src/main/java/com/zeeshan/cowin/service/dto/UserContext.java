package com.zeeshan.cowin.service.dto;

import com.zeeshan.cowin.dto.BeneficiariesResponse;
import lombok.Data;
import org.apache.logging.log4j.util.Strings;

import java.util.List;

@Data
public class UserContext {
    private Long chatId;
    private String txnId;
    private String mobile;
    private String token;
    private String sessionId;
    private String centerId;
    private String referenceId;
    private String action = Strings.EMPTY;
    private String previousAction = Strings.EMPTY;
    private String captcha;
    private int defaultSlot = 0;
    private String selectedSlot;
    private String selectedPincode;
    private String selectedDistrictId;
    private boolean selectAnySlot;
    private String[] slots;
    private BeneficiariesResponse.Beneficiary beneficiarySelected;
    private List<BeneficiariesResponse.Beneficiary> beneficiaries;
}
