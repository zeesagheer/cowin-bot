package com.zeeshan.cowin.service;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.response.SendResponse;

public interface TelegramBotService {
    Boolean executeAction(Update update);

    SendResponse sendMessage(String message);
}
