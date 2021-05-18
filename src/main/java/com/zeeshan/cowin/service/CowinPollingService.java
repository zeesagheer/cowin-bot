package com.zeeshan.cowin.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.zeeshan.cowin.adapter.TelegramBotAdapter;
import com.zeeshan.cowin.dto.PlanRequest;
import com.zeeshan.cowin.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class CowinPollingService {


    @Autowired
    TelegramBotAdapter telegramBotAdapter;

    @Autowired
    CowinService cowinService;

    @Autowired
    TelegramBot telegramBot;

    @Value("${telegram.bot.group.chat.id}")
    String chatId;

    Set<String> projectedSessions = new HashSet<>();

    static private final String pinCode = "110025";
    static private final String districtId = "144";

    @Scheduled(cron = "${poller.cron}")
    public void poll() {
        try {
            log.info("Start poll...");
            PlanRequest planRequest = new PlanRequest();
            planRequest.setPinCode(pinCode);
//            planRequest.setDistrictId(districtId);
            planRequest.setOnlyFree(false);
            planRequest.setEighteenPlusOnly(false);
            planRequest.setDose1(null);
            planRequest.setVaccineList(new HashSet<>(Arrays.asList("COVAXIN", "COVISHIELD")));
            planRequest.setSkipSessions(projectedSessions);
            List<Result> results = cowinService.getPlans(planRequest);
            log.info("Results : {}", results);
            results.forEach(result -> {
                String builder = "*Name :* " + result.getName() +
                        "\n*Address :* " + result.getAddress() +
                        "\n*Dose1 :* " + result.getDose1() +
                        "  *Dose2 :* " + result.getDose2() +
                        "\n*Age :* " + result.getAge() +
                        "\n*Date :* " + result.getDate() +
                        "\n*Fees :* " + result.getFees() +
                        "\n*SessionId :*$ " + result.getSlotsIntervals() + "$" + result.getCenterId() + "$" + result.getSessionId();
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
        log.info("End poll...");
    }

}
