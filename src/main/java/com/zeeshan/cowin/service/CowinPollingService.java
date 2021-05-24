package com.zeeshan.cowin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.zeeshan.cowin.adapter.OtpAdapter;
import com.zeeshan.cowin.adapter.TelegramBotAdapter;
import com.zeeshan.cowin.dto.PlanRequest;
import com.zeeshan.cowin.dto.Result;
import com.zeeshan.cowin.service.dto.UserContext;
import com.zeeshan.cowin.service.impl.TelegramBotServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CowinPollingService {

    @Autowired
    TelegramBotAdapter telegramBotAdapter;

    @Autowired
    OtpAdapter otpAdapter;

    @Autowired
    CowinService cowinService;

    @Autowired
    TelegramBot telegramBot;

    @Autowired
    TelegramBotService telegramBotService;

    @Autowired
    ObjectMapper mapper;

    @Value("${telegram.bot.group.chat.id}")
    String chatId;

    Set<String> projectedSessions = new HashSet<>();
    Set<String> projectedSessionsWithToken = new HashSet<>();

    public static Map<String, Set<String>> districtPinCodeMap = new ConcurrentHashMap<>();

    @Scheduled(cron = "${poller.cron}")
    public void poll() {
        log.info("Start poll...");
        Set<String> registeredPinCodes = new HashSet<>(TelegramBotServiceImpl.pinCodeRegistrationMap.keySet());
        TelegramBotServiceImpl.districtRegistrationMap.keySet()
                .parallelStream()
                .filter(districtId -> !TelegramBotServiceImpl.districtRegistrationMap.get(districtId).isEmpty())
                .parallel()
                .forEach(districtId -> {
                    fetchPlans(districtId, true);
                    if (districtPinCodeMap.containsKey(districtId)) {
                        registeredPinCodes.removeAll(districtPinCodeMap.get(districtId));
                    }
                });
        registeredPinCodes.parallelStream()
                .filter(pinCode -> !TelegramBotServiceImpl.pinCodeRegistrationMap.get(pinCode).isEmpty())
                .parallel()
                .forEach(pinCode -> fetchPlans(pinCode, false));
        log.info("End poll...");
    }

    public void fetchPlans(String query, boolean isDistrict) {
        try {
            PlanRequest planRequest = new PlanRequest();
            if (isDistrict) {
                planRequest.setDistrictId(query);
                log.info("Call for district {}", query);
            } else {
                planRequest.setPinCode(query);
                log.info("Call for pincode {}", query);
            }
            planRequest.setSkipSessions(projectedSessions);
            TelegramBotServiceImpl.userContextMap.values().stream()
                    .filter(UserContext::isAdmin)
                    .filter(userContext -> TelegramBotServiceImpl.verifyToken(userContext.getToken(), 1))
                    .findAny().ifPresent(userContext -> {
                planRequest.setToken(userContext.getToken());
                planRequest.setSkipSessions(projectedSessionsWithToken);
                log.info("Setting token with admin user : {}", userContext.getMobile());
            });
            List<Result> results = cowinService.getPlans(planRequest);
            if (isDistrict && !planRequest.getPinCodesInDistrict().isEmpty()) {
                districtPinCodeMap.put(query, planRequest.getPinCodesInDistrict());
            }
            log.info("Results for {}-{} : {}", isDistrict ? "district" : "pincode", query, mapper.writeValueAsString(results));

            Set<Long> chatIds = Collections.synchronizedSet(new HashSet<>());
            if (isDistrict && null != TelegramBotServiceImpl.districtRegistrationMap.get(query)) {
                chatIds.addAll(TelegramBotServiceImpl.districtRegistrationMap.get(query));
            }
            results.parallelStream().collect(Collectors.groupingBy(Result::getPinCode, Collectors.toList()))
                    .values().stream()
                    .filter(resultList -> TelegramBotServiceImpl.pinCodeRegistrationMap
                            .containsKey(resultList.get(0).getPinCode()))
                    .parallel().forEach(resultList -> {
                Set<Long> registeredUsers = TelegramBotServiceImpl.pinCodeRegistrationMap
                        .get(resultList.get(0).getPinCode());
                telegramBotService.sendToRegisteredUsers(registeredUsers, resultList);
                chatIds.removeAll(registeredUsers);
            });
            results.parallelStream().map(Result::getSessionId).forEach(planRequest.getSkipSessions()::add);
            telegramBotService.sendToRegisteredUsers(chatIds, results);

        } catch (IOException e) {
            log.error("Error while fetching slots", e);
        }
    }

    public static String resultConverter(Result result) {
        return "*Name :* " + result.getName() +
                "\n*Address :* " + result.getAddress() +
                "\n*Dose1 :* " + result.getDose1() +
                "  *Dose2 :* " + result.getDose2() +
                "\n*Pincode :* " + result.getPinCode() +
                "\n*Vaccine :* " + result.getVaccine() +
                "\n*Age :* " + result.getAge() +
                "\n*Date :* " + result.getDate() +
                "\n*Fees :* " + result.getFees() +
                "\n*SessionId :*$ " + result.getSlotsIntervals() + "$" + result.getCenterId() + "$" + result.getSessionId();
    }

}
