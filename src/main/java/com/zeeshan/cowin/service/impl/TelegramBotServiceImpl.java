package com.zeeshan.cowin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.zeeshan.cowin.service.TelegramBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;

@Service
@Slf4j
public class TelegramBotServiceImpl implements TelegramBotService {
    @Autowired
    TelegramBot telegramBot;

    @Autowired
    ObjectMapper mapper;

    @Value("${telegram.bot.group.chat.id}")
    String chatId;

    @Override
    public Boolean executeAction(Update update) {
        Message message = update.message();
        try {
            log.info(mapper.writeValueAsString(update));
        } catch (JsonProcessingException e) {
            log.info(update.toString(), e);
        }
//        Long chatId = message.from();
        Long chatId = message.chat().id();
        String text = message.text();
        KeyboardButton keyboardButton = new KeyboardButton("Push to share contact");
        keyboardButton.requestContact(true);
//        SendMessage sendMessage = new SendMessage(String.valueOf(chatId));
//        sendMessage.replyMarkup(new ReplyKeyboardMarkup(keyboardButton));
        return null;
    }

    @Override
    public SendResponse sendMessage(String message) {
        return telegramBot.execute(new SendMessage(chatId, message));
    }
}
