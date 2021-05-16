package com.zeeshan.cowin.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeeshan.cowin.dto.CowinRequest;
import com.zeeshan.cowin.dto.CowinResponse;
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

    static private final String URL_PIN = "https://cdn-api.co-vin.in/api/v2/appointment/sessions/public/calendarByPin";
    static private final String URL_DISTRICT = "https://cdn-api.co-vin.in/api/v2/appointment/sessions/public/calendarByDistrict";

    public ResponseEntity<CowinResponse> getPostpaidPlans(CowinRequest postpaidPlanRequest) throws IOException {

        UriComponentsBuilder builder = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "*/*");
            headers.set("User-Agent", "PostmanRuntime/7.28.0");
            headers.set("Host", "cdn-api.co-vin.in");
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


}
