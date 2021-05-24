package com.zeeshan.cowin.service;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.response.SendResponse;
import com.zeeshan.cowin.dto.Result;
import com.zeeshan.cowin.dto.VerifyOtpResponse;
import com.zeeshan.cowin.service.dto.UserContext;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface TelegramBotService {
    void sendToRegisteredUsers(Set<Long> chatList, List<Result> results);

    Boolean executeAction(Update update);

    VerifyOtpResponse verifyOtp(UserContext userContext, String otp) throws IOException;

    void sendOtp(UserContext userContext, String mobile, boolean updateUser) throws IOException;

    SendResponse removeKeyboard(UserContext userContext);

    SendResponse sendMessage(Long chatId, String message);

    SendResponse sendMessage(String message);
}
