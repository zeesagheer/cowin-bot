package com.zeeshan.cowin.service.impl;

import com.zeeshan.cowin.adapter.CowinPageAdapter;
import com.zeeshan.cowin.dto.CowinRequest;
import com.zeeshan.cowin.dto.CowinResponse;
import com.zeeshan.cowin.dto.PlanRequest;
import com.zeeshan.cowin.dto.Result;
import com.zeeshan.cowin.entities.CowinResult;
import com.zeeshan.cowin.repositories.CowinResultsRepo;
import com.zeeshan.cowin.service.CowinService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CowinServiceImpl implements CowinService {


    static private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    static private final String FREE = "Free";


    @Autowired
    CowinPageAdapter cowinPageAdapter;

    @Autowired
    CowinResultsRepo repo;

    @Override
    public List<Result> getPlans(PlanRequest planRequest) throws IOException {
        CowinRequest request = new CowinRequest();
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("+05:30"));
        request.setDate(now.format(dateTimeFormatter));
        request.setPinCode(planRequest.getPinCode());
        request.setDistrictId(planRequest.getDistrictId());
        request.setToken(planRequest.getToken());
        ResponseEntity<CowinResponse> response = cowinPageAdapter.getSlots(request);
        List<Result> results = new ArrayList<>();
        if (response.getBody() != null) {
            List<CowinResponse.Centers> centers = response.getBody().getCenters();
            if (StringUtils.isNotEmpty(planRequest.getDistrictId())) {
                centers.forEach(center -> planRequest.getPinCodesInDistrict().add(center.getPincode()));
            }
            centers.forEach(center -> {
                Map<String, String> priceMap = new HashMap<>();
                if (null != center.getVaccine_fees()) {
                    priceMap.putAll(center.getVaccine_fees().stream()
                            .collect(Collectors
                                    .toMap(CowinResponse.Centers.Vaccine::getVaccine,
                                            CowinResponse.Centers.Vaccine::getFee)));
                }
                List<CowinResult> debugList = new ArrayList<>();
                center.getSessions().stream()
                        .filter(session -> session.getAvailable_capacity() > 0)
                        .peek(session -> debugList.add(convert(center, session, planRequest, UUID.randomUUID().toString())))
                        .filter(session -> !planRequest.getSkipSessions().contains(session.getSession_id()))
                        .forEach(session -> results.add(convert(center, priceMap, session)));
                repo.saveAll(debugList);
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


    private CowinResult convert(CowinResponse.Centers center, CowinResponse.Centers.Session session, PlanRequest planRequest, String uuid) {
        CowinResult result = new CowinResult();
        result.setCenterId(center.getCenter_id());
        result.setName(center.getName());
        result.setPinCode(center.getPincode());
        result.setAge(session.getMin_age_limit());
        result.setDate(session.getDate());
        result.setVaccine(session.getVaccine());
        result.setDose(session.getAvailable_capacity());
        result.setDose1(session.getAvailable_capacity_dose1());
        result.setDose2(session.getAvailable_capacity_dose2());
        result.setSessionId(session.getSession_id());

        result.setSearchedDistrictId(planRequest.getDistrictId());
        result.setSearchedPinCode(planRequest.getPinCode());
        result.setIsToken(StringUtils.isNotEmpty(planRequest.getToken()));
        result.setUuid(uuid);

        return result;
    }
}
