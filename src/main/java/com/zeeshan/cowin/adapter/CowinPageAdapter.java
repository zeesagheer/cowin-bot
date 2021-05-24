package com.zeeshan.cowin.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeeshan.cowin.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@Slf4j
public class CowinPageAdapter {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    static private final String URL_OTP = "https://cdn-api.co-vin.in/api/v2/auth/generateMobileOTP";
    static private final String URL_OTP_VALIDATE = "https://cdn-api.co-vin.in/api/v2/auth/validateMobileOtp";
    static private final String URL_PIN = "https://cdn-api.co-vin.in/api/v2/appointment/sessions/public/calendarByPin";
    static private final String URL_DISTRICT = "https://cdn-api.co-vin.in/api/v2/appointment/sessions/public/calendarByDistrict";
    static private final String URL_BENIFECIARIES = "https://cdn-api.co-vin.in/api/v2/appointment/beneficiaries";
    static private final String URL_CAPTCHA = "https://cdn-api.co-vin.in/api/v2/auth/getRecaptcha";
    static private final String URL_SCHEDULE = "https://cdn-api.co-vin.in/api/v2/appointment/schedule";

    public ResponseEntity<CowinResponse> getSlots(CowinRequest postpaidPlanRequest) throws IOException {

        UriComponentsBuilder builder = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "*/*");
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36");
            if (StringUtils.isNotEmpty(postpaidPlanRequest.getToken())) {
                headers.setBearerAuth(postpaidPlanRequest.getToken());
            }
            if (StringUtils.isNotEmpty(postpaidPlanRequest.getDistrictId())) {
                builder = UriComponentsBuilder.fromHttpUrl(URL_DISTRICT).
                        queryParam("district_id", postpaidPlanRequest.getDistrictId());
            } else {
                builder = UriComponentsBuilder.fromHttpUrl(URL_PIN)
                        .queryParam("pincode", postpaidPlanRequest.getPinCode());
            }
            builder.queryParam("date", postpaidPlanRequest.getDate());
            HttpEntity<CowinResponse> request = new HttpEntity<>(headers);
            return restTemplate.exchange(builder.toUriString(), HttpMethod.GET, request, CowinResponse.class);
        } catch (HttpStatusCodeException e) {
            String responseString = e.getResponseBodyAsString();
            log.error("Request {} , Error Response {}", builder.toUriString(), responseString);
            CowinResponse responseObject = objectMapper.readValue(responseString, CowinResponse.class);
            return new ResponseEntity<>(responseObject, e.getStatusCode());
        }

    }

    public ResponseEntity<GenerateOtpResponse> generateOTP(GenerateOtpRequest generateOtpRequest) throws IOException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "*/*");
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36");
            HttpEntity<GenerateOtpRequest> request = new HttpEntity<>(generateOtpRequest, headers);
            return restTemplate.exchange(URL_OTP, HttpMethod.POST, request, GenerateOtpResponse.class);
        } catch (HttpStatusCodeException e) {
            log.error("dsds", e);
            String responseString = e.getResponseBodyAsString();
            log.error("Request {} , Error Response {}", URL_OTP, responseString);
            GenerateOtpResponse responseObject = objectMapper.readValue(responseString, GenerateOtpResponse.class);
            return new ResponseEntity<>(responseObject, e.getStatusCode());
        }

    }


    public ResponseEntity<VerifyOtpResponse> verifyOTP(VerifyOtpRequest verifyOtpRequest) throws IOException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "*/*");
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36");
            HttpEntity<VerifyOtpRequest> request = new HttpEntity<>(verifyOtpRequest, headers);
            return restTemplate.exchange(URL_OTP_VALIDATE, HttpMethod.POST, request, VerifyOtpResponse.class);
        } catch (HttpStatusCodeException e) {
            String responseString = e.getResponseBodyAsString();
            log.error("Request {} , Error Response {}", URL_OTP_VALIDATE, responseString);
            VerifyOtpResponse responseObject = objectMapper.readValue(responseString, VerifyOtpResponse.class);
            return new ResponseEntity<>(responseObject, e.getStatusCode());
        }
    }

    public ResponseEntity<BeneficiariesResponse> getBeneficiaries(String token) throws IOException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "*/*");
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36");
            headers.setBearerAuth(token);
            HttpEntity<CowinResponse> request = new HttpEntity<>(headers);
            return restTemplate.exchange(URL_BENIFECIARIES, HttpMethod.GET, request, BeneficiariesResponse.class);
        } catch (HttpStatusCodeException e) {
            String responseString = e.getResponseBodyAsString();
            log.error("Request {} , Error Response {}", URL_BENIFECIARIES, responseString);
            BeneficiariesResponse responseObject = objectMapper.readValue(responseString, BeneficiariesResponse.class);
            return new ResponseEntity<>(responseObject, e.getStatusCode());
        }

    }


    public ResponseEntity<CaptchaResponse> getCaptcha(String token) throws IOException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "*/*");
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36");
            headers.setBearerAuth(token);
            HttpEntity<CowinResponse> request = new HttpEntity<>(headers);
            return restTemplate.exchange(URL_CAPTCHA, HttpMethod.POST, request, CaptchaResponse.class);
        } catch (HttpStatusCodeException e) {
            String responseString = e.getResponseBodyAsString();
            log.error("Request {} , Error Response {}", URL_CAPTCHA, responseString);
            CaptchaResponse responseObject = objectMapper.readValue(responseString, CaptchaResponse.class);
            return new ResponseEntity<>(responseObject, e.getStatusCode());
        }

    }


    public ResponseEntity<ScheduleResponse> schedule(ScheduleRequest scheduleRequest, String token) throws IOException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "*/*");
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36");
            headers.setBearerAuth(token);
            HttpEntity<ScheduleRequest> request = new HttpEntity<>(scheduleRequest, headers);
            log.info("Logging response for book slot : " + request);
            return restTemplate.exchange(URL_SCHEDULE, HttpMethod.POST, request, ScheduleResponse.class);
        } catch (HttpStatusCodeException e) {
            String responseString = e.getResponseBodyAsString();
            log.error("Request {} , Error Response {}", URL_SCHEDULE, responseString);
            ScheduleResponse responseObject = objectMapper.readValue(responseString, ScheduleResponse.class);
            return new ResponseEntity<>(responseObject, e.getStatusCode());
        }
    }


}
