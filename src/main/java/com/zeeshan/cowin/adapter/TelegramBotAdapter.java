package com.zeeshan.cowin.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeeshan.cowin.dto.CowinResponse;
import com.zeeshan.cowin.dto.TelegramResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class TelegramBotAdapter {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    static private final String URL = "https://api.telegram.org/bot%s/sendMessage";
    static private final String BOT_ID = "1705924595";
    static private final String BOT_KEY = "1705924595:AAFwi1D7FlLGhBWdhQgVLqzEKjwz1jvJC4c";
    static private final String GROUP_ID = "563425825";

    public ResponseEntity<TelegramResponse> postToTelegram(String message) throws IOException {

        UriComponentsBuilder builder = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            builder = UriComponentsBuilder.fromHttpUrl(String.format(URL, BOT_ID + ":" + BOT_KEY))
                    .queryParam("chat_id", ("-" + GROUP_ID))
                    .queryParam("text", message);
            HttpEntity<CowinResponse> request = new HttpEntity<>(headers);
            return restTemplate.exchange(builder.encode().toUriString(), HttpMethod.GET, request, TelegramResponse.class);
        } catch (HttpStatusCodeException e) {
            String responseString = e.getResponseBodyAsString();
            log.error("Request {} , Error Response {}", builder.toUriString(), responseString);
            TelegramResponse responseObject = objectMapper.readValue(responseString, TelegramResponse.class);
            return new ResponseEntity<>(responseObject, e.getStatusCode());
        }

    }


}
