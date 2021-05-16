package com.zeeshan.cowin.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.zeeshan.cowin.adapter.CowinPageAdapter;
import com.zeeshan.cowin.adapter.TelegramBotAdapter;
import com.zeeshan.cowin.dto.CowinRequest;
import com.zeeshan.cowin.dto.CowinResponse;
import com.zeeshan.cowin.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CowinPollingService {

    @Autowired
    CowinPageAdapter cowinPageAdapter;

    @Autowired
    TelegramBotAdapter telegramBotAdapter;

    @Autowired
    TelegramBot telegramBot;

    @Value("${telegram.bot.group.chat.id}")
    String chatId;

    Set<String> projectedSessions = new HashSet<>();

    static private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    static private final String FREE = "Free";
    static private final String pinCode = "110025";
    static private final String districtId = "144";
    static private final boolean onlyFree = false;
    static private final boolean eighteenPlusOnly = false;
    static private final boolean dose1 = false;
    static private final List<String> vaccineList = Arrays.asList("COVAXIN", "COVISHIELD");

    @Scheduled(cron = "${poller.cron}")
    public void poll() {
        log.info("Start poll...");
        CowinRequest request = new CowinRequest();
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("+05:30"));
        request.setDate(now.format(dateTimeFormatter));
        request.setPinCode(pinCode);
        try {
            ResponseEntity<CowinResponse> response = cowinPageAdapter.getPostpaidPlans(request);
            List<Result> results = new ArrayList<>();
            if (response.getBody() != null) {
                List<CowinResponse.Centers> centers = response.getBody().getCenters();
                centers.stream().filter(center -> !onlyFree || FREE.equalsIgnoreCase(center.getFee_type()))
                        .forEach(center -> {
                            Map<String, String> priceMap = new HashMap<>();
                            if (null != center.getVaccine_fees()) {
                                priceMap.putAll(center.getVaccine_fees().stream()
                                        .collect(Collectors
                                                .toMap(CowinResponse.Centers.Vaccine::getVaccine,
                                                        CowinResponse.Centers.Vaccine::getFee)));
                            }
                            center.getSessions().stream()
                                    .filter(session -> !projectedSessions.contains(session.getSession_id()))
                                    .filter(session -> dose1
                                            ? session.getAvailable_capacity_dose1() > 0
                                            : session.getAvailable_capacity_dose2() > 0)
                                    .filter(session -> !eighteenPlusOnly || session.getMin_age_limit() == 18)
                                    .filter(session -> vaccineList.isEmpty() || vaccineList.contains(session.getVaccine()))
                                    .forEach(session -> {
                                        results.add(convert(center, priceMap, session));
                                    });
                        });
            }
            log.info("Results : {}", results);
            results.forEach(result -> {
                String builder = "*Name :* " + result.getName() +
                        "\n*Address :* " + result.getAddress() +
                        "\n*Dose1 :* " + result.getDose1() +
                        "\n*Dose2 :* " + result.getDose2() +
                        "\n*Age :* " + result.getAge() +
                        "\n*Date :* " + result.getDate() +
                        "\n*Fees :* " + result.getFees() +
                        "\n*SessionId :* " + result.getSessionId();
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
