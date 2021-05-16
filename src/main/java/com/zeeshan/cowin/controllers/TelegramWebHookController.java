package com.zeeshan.cowin.controllers;

import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.response.SendResponse;
import com.zeeshan.cowin.service.TelegramBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
public class TelegramWebHookController {

    @Autowired
    TelegramBotService telegramBotService;

    @PostMapping(value = "/bot")
    public ResponseEntity<Boolean> bot(@RequestBody Update update) {
        return new ResponseEntity<>(telegramBotService.executeAction(update), HttpStatus.OK);
    }

    @GetMapping(value = "/test")
    public ResponseEntity<String> test() {
        log.info("its working");
        return new ResponseEntity<>("working...", HttpStatus.OK);
    }

    @GetMapping(value = "/msg")
    public ResponseEntity<SendResponse> msg(@RequestParam String message) {
        return new ResponseEntity<>(telegramBotService.sendMessage(message), HttpStatus.OK);
    }
}
