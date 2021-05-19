package com.zeeshan.cowin.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.zeeshan.cowin.adapter.TelegramBotAdapter;
import com.zeeshan.cowin.dto.PlanRequest;
import com.zeeshan.cowin.dto.Result;
import com.zeeshan.cowin.service.impl.TelegramBotServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CowinPollingService {

    @Autowired
    TelegramBotAdapter telegramBotAdapter;

    @Autowired
    CowinService cowinService;

    @Autowired
    TelegramBot telegramBot;

    @Autowired
    TelegramBotService telegramBotService;

    @Value("${telegram.bot.group.chat.id}")
    String chatId;

    Set<String> projectedSessions = new HashSet<>();

    @Scheduled(cron = "${poller.cron}")
    public void poll() {
        log.info("Start poll...");
        Set<String> pinCodes = Collections.synchronizedSet(new HashSet<>());
        TelegramBotServiceImpl.districtRegistrationMap.keySet()
                .parallelStream()
                .filter(districtId -> !TelegramBotServiceImpl.districtRegistrationMap.get(districtId).isEmpty())
                .parallel()
                .forEach(districtId -> pinCodes.addAll(fetchPlans(districtId, true)));
        Set<String> registeredPinCodes = new HashSet<>(TelegramBotServiceImpl.districtRegistrationMap.keySet());
        registeredPinCodes.removeAll(pinCodes);
        registeredPinCodes.parallelStream()
                .filter(pinCode -> !TelegramBotServiceImpl.pinCodeRegistrationMap.get(pinCode).isEmpty())
                .parallel()
                .forEach(pinCode -> fetchPlans(pinCode, false));
        log.info("End poll...");
    }

    public Set<String> fetchPlans(String query, boolean isDistrict) {
        Set<String> pinCodesInDistrict = new HashSet<>();
        try {
            PlanRequest planRequest = new PlanRequest();
            if (isDistrict) {
                planRequest.setDistrictId(query);
                log.info("Call for district {}", query);
            } else {
                planRequest.setPinCode(query);
                log.info("Call for pincode {}", query);
            }
            planRequest.setOnlyFree(false);
            planRequest.setEighteenPlusOnly(false);
            planRequest.setDose1(null);
            planRequest.setVaccineList(new HashSet<>(Arrays.asList("COVAXIN", "COVISHIELD")));
            planRequest.setSkipSessions(projectedSessions);
            List<Result> results = cowinService.getPlans(planRequest);
            pinCodesInDistrict = planRequest.getPinCodesInDistrict();
            log.info("Results : {}", results);

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
            telegramBotService.sendToRegisteredUsers(chatIds, results);

            results.forEach(result -> {
                String builder = resultConverter(result);
                SendMessage message = new SendMessage(chatId, builder);
                message.parseMode(ParseMode.Markdown);
                SendResponse sendResponse = telegramBot.execute(message);
                if (sendResponse.isOk()) {
                    projectedSessions.add(result.getSessionId());
                }
                log.info("ok={}, error_code={}, description={}, parameters={}", sendResponse.isOk(), sendResponse.errorCode(), sendResponse.description(), sendResponse.parameters());
            });
        } catch (IOException e) {
            log.error("Error while fetching slots", e);
        }
        return pinCodesInDistrict;
    }

    public static String resultConverter(Result result) {
        String builder = "*Name :* " + result.getName() +
                "\n*Address :* " + result.getAddress() +
                "\n*Dose1 :* " + result.getDose1() +
                "  *Dose2 :* " + result.getDose2() +
                "\n*Age :* " + result.getAge() +
                "\n*Date :* " + result.getDate() +
                "\n*Fees :* " + result.getFees() +
                "\n*SessionId :*$ " + result.getSlotsIntervals() + "$" + result.getCenterId() + "$" + result.getSessionId();
        return builder;
    }

}
