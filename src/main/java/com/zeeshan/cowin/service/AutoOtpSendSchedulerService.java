package com.zeeshan.cowin.service;

import com.zeeshan.cowin.adapter.OtpAdapter;
import com.zeeshan.cowin.dto.OTPResponse;
import com.zeeshan.cowin.dto.VerifyOtpResponse;
import com.zeeshan.cowin.service.dto.UserContext;
import com.zeeshan.cowin.service.impl.TelegramBotServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AutoOtpSendSchedulerService {

    @Autowired
    OtpAdapter otpAdapter;

    @Autowired
    TelegramBotService telegramBotService;

    @Scheduled(cron = "${otp.send.cron}")
    public void pollOtpSend() {
        log.info("Start otp send poll...");
        Long currTime = new Date().getTime();
        TelegramBotServiceImpl.userContextMap.values().stream()
                .filter(UserContext::isAdmin)
                .filter(userContext -> StringUtils.isNotEmpty(userContext.getMobile()))
                .filter(userContext -> !TelegramBotServiceImpl.verifyToken(userContext.getToken(), 2))
                .filter(userContext -> null == userContext.getOtpSentTime() || currTime - userContext.getOtpSentTime() >= 30000)
                .forEach(userContext -> {
                    try {
                        telegramBotService.sendOtp(userContext, userContext.getMobile(), false);
                    } catch (Exception e) {
                        log.error("Error occurrerd while sending otp for user {}", userContext.getMobile(), e);
                    }
                });

        log.info("End otp send poll...");
    }

    @Scheduled(cron = "${otp.get.cron}")
    public void pollGetOtp() throws IOException {
        log.info("Start otp get poll...");
        Long currTime = new Date().getTime();
        List<UserContext> userContexts = TelegramBotServiceImpl.userContextMap.values().stream()
                .filter(UserContext::isAdmin)
                .filter(userContext -> StringUtils.isNotEmpty(userContext.getTxnId()))
                .filter(userContext -> !TelegramBotServiceImpl.verifyToken(userContext.getToken(), 2))
                .filter(userContext -> null != userContext.getOtpSentTime()
                        && (currTime - userContext.getOtpSentTime() >= 10000)
                        && (currTime - userContext.getOtpSentTime() <= 60000))
                .collect(Collectors.toList());

        if (!userContexts.isEmpty()) {
            Map<String, OTPResponse> map = otpAdapter.getOtp().getBody();
            userContexts.parallelStream().forEach(userContext -> {
                OTPResponse response = map.get(String.valueOf(userContext.getChatId()));
                if (null != response
                        && userContext.getOtpSentTime() < response.getTime()
                        && userContext.getOtpSentTime() - response.getTime() <= 120000) {
                    try {
                        VerifyOtpResponse verificationResponse = telegramBotService.verifyOtp(userContext, response.getOtp());
                        if (StringUtils.isEmpty(verificationResponse.getErrorCode())
                                && StringUtils.isNotEmpty(verificationResponse.getToken())) {
                            userContext.setToken(verificationResponse.getToken());
                            log.info("user {} authenticated", userContext.getMobile());
                            if ("Enter Otp".equalsIgnoreCase(userContext.getAction())) {
                                telegramBotService.sendMessage(userContext.getChatId(), "Authenticated");
                                telegramBotService.removeKeyboard(userContext);
                                if ("Enter Otp".equalsIgnoreCase(userContext.getEndAction())) {
                                    userContext.setAction(Strings.EMPTY);
                                    userContext.setEndAction(Strings.EMPTY);
                                } else {
                                    userContext.setAction("Choose Beneficiary");
                                }
                            }
                        } else {
                            log.error("Error response for user {} : {}", userContext.getMobile(), verificationResponse.getError());
                        }
                    } catch (IOException e) {
                        log.error("Error while verifying otp for user {}", userContext.getMobile(), e);
                    }
                }
            });
        }
        log.info("end otp send poll...");
    }

}
