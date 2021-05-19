package com.zeeshan.cowin.service;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.response.SendResponse;
import com.zeeshan.cowin.dto.Result;

import java.util.List;
import java.util.Set;

public interface TelegramBotService {
    void sendToRegisteredUsers(Set<Long> chatList, List<Result> results);

    Boolean executeAction(Update update);

    SendResponse sendMessage(String message);
}
