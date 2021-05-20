package com.zeeshan.cowin.service.impl;

import com.zeeshan.cowin.adapter.CowinPageAdapter;
import com.zeeshan.cowin.dto.CowinRequest;
import com.zeeshan.cowin.dto.CowinResponse;
import com.zeeshan.cowin.dto.PlanRequest;
import com.zeeshan.cowin.dto.Result;
import com.zeeshan.cowin.service.CowinService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CowinServiceImpl implements CowinService {


    static private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    static private final String FREE = "Free";


    @Autowired
    CowinPageAdapter cowinPageAdapter;

    @Override
    public List<Result> getPlans(PlanRequest planRequest) throws IOException {
        CowinRequest request = new CowinRequest();
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("+05:30"));
        request.setDate(now.format(dateTimeFormatter));
        request.setPinCode(planRequest.getPinCode());
        request.setDistrictId(planRequest.getDistrictId());
        ResponseEntity<CowinResponse> response = cowinPageAdapter.getSlots(request);
        List<Result> results = new ArrayList<>();
        if (response.getBody() != null) {
            List<CowinResponse.Centers> centers = response.getBody().getCenters();
            if (StringUtils.isNotEmpty(planRequest.getDistrictId())) {
                centers.forEach(center -> planRequest.getPinCodesInDistrict().add(center.getPincode()));
            }
            centers.stream()
                    .forEach(center -> {
                        Map<String, String> priceMap = new HashMap<>();
                        if (null != center.getVaccine_fees()) {
                            priceMap.putAll(center.getVaccine_fees().stream()
                                    .collect(Collectors
                                            .toMap(CowinResponse.Centers.Vaccine::getVaccine,
                                                    CowinResponse.Centers.Vaccine::getFee)));
                        }
                        center.getSessions().stream()
                                .filter(session -> !planRequest.getSkipSessions().contains(session.getSession_id()))
                                .filter(session -> session.getAvailable_capacity() > 0)
                                .forEach(session -> {
                                    results.add(convert(center, priceMap, session));
                                });
                    });
        }
        return results;
    }


    private Result convert(CowinResponse.Centers center, Map<String, String> priceMap, CowinResponse.Centers.Session session) {
        Result result = new Result();
        result.setAddress(center.getAddress());
        result.setCenterId(center.getCenter_id());
        result.setName(center.getName());
        result.setPinCode(center.getPincode());
        result.setAge(session.getMin_age_limit());
        result.setDate(session.getDate());
        result.setVaccine(session.getVaccine());
        result.setSlots(session.getAvailable_capacity());
        result.setDose1(session.getAvailable_capacity_dose1());
        result.setDose2(session.getAvailable_capacity_dose2());
        result.setSessionId(session.getSession_id());
        result.setSlotsIntervals(session.getSlots());
        if (FREE.equalsIgnoreCase(center.getFee_type())) {
            result.setFees(FREE);
        } else if (null != session.getVaccine() && priceMap.containsKey(session.getVaccine().toUpperCase())) {
            result.setFees(priceMap.get(session.getVaccine().toUpperCase()));
        } else {
            result.setFees("No Info");
        }
        return result;
    }
}
