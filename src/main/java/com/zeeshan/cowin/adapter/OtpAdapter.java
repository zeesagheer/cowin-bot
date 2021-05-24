package com.zeeshan.cowin.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeeshan.cowin.dto.CowinResponse;
import com.zeeshan.cowin.dto.OTPResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

@Component
@Slf4j
public class OtpAdapter {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    static private final String URL = "https://cowinsms.herokuapp.com/getotp";

    public ResponseEntity<Map<String, OTPResponse>> getOtp() throws IOException {
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<CowinResponse> request = new HttpEntity<>(headers);
            return restTemplate.exchange(URL, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, OTPResponse>>() {
            });
        } catch (HttpStatusCodeException e) {
            String responseString = e.getResponseBodyAsString();
            log.error("Request {} , Error Response {}", URL, responseString);
            Map<String, OTPResponse> responseObject = objectMapper.readValue(responseString, new TypeReference<Map<String, OTPResponse>>() {
            });
            return new ResponseEntity<>(responseObject, e.getStatusCode());
        }

    }


}
