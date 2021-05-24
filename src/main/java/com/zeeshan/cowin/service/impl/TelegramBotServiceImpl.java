package com.zeeshan.cowin.service.impl;

import com.auth0.jwt.JWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Contact;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.MessageEntity;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.request.SetWebhook;
import com.pengrad.telegrambot.response.SendResponse;
import com.zeeshan.cowin.adapter.CowinPageAdapter;
import com.zeeshan.cowin.dto.*;
import com.zeeshan.cowin.service.TelegramBotService;
import com.zeeshan.cowin.service.dto.UserContext;
import lombok.extern.slf4j.Slf4j;
import ngrok.NgrokInitializedEvent;
import ngrok.api.NgrokApiClient;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.JPEGTranscoder;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.zeeshan.cowin.service.CowinPollingService.resultConverter;
import static java.nio.charset.StandardCharsets.UTF_8;

@Service
@Slf4j
public class TelegramBotServiceImpl implements TelegramBotService {
    @Autowired
    TelegramBot telegramBot;

    @Autowired
    CowinPageAdapter cowin;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    private NgrokApiClient ngrok;

    @Value("${telegram.bot.group.chat.id}")
    String chatId;

    public static Map<Long, UserContext> userContextMap = new HashMap<>();

    public static Map<String, Set<Long>> pinCodeRegistrationMap = new ConcurrentHashMap<>();

    public static Map<String, Set<Long>> districtRegistrationMap = new ConcurrentHashMap<>();

    @EventListener
    public void run(NgrokInitializedEvent event) {
        String httpsTunnelUrl = ngrok.getHttpsTunnelUrl();
        String webhookUrl = httpsTunnelUrl + "/bot";
        log.info("Web hook is set : {} for url {}", telegramBot.execute(new SetWebhook().url(webhookUrl)).isOk(), webhookUrl);
    }

    @Override
    public void sendToRegisteredUsers(Set<Long> chatList, List<Result> results) {
        if (!CollectionUtils.isEmpty(results)) {
            chatList.parallelStream().map(userContextMap::get).parallel().forEach(userContext -> {
                if (userContext.isSelectAnySlot()) {
                    try {
                        List<Result> selectEligibleSlots = selectEligibleSlots(userContext, results);
                        if (!CollectionUtils.isEmpty(selectEligibleSlots)) {
                            List<Result> preferredVaccine = selectEligibleSlots.stream()
                                    .filter(slot -> CollectionUtils.isEmpty(userContext.getPreferredVaccine())
                                            || userContext.getPreferredVaccine().contains(slot.getVaccine().toUpperCase()))
                                    .collect(Collectors.toList());
                            Optional<Result> optionalResult = preferredVaccine.stream()
                                    .filter(slot -> slot.getPinCode().equalsIgnoreCase(userContext.getSelectedPincode())).findAny();
                            Result result = null;
                            if (optionalResult.isPresent()) {
                                result = optionalResult.get();
                            } else if (!preferredVaccine.isEmpty()) {
                                result = preferredVaccine.get(0);
                            }
                            if (result != null) {
                                sendMarkDownMessage(userContext.getChatId(), resultConverter(result));
                                userFlow(userContext, resultConverter(result), null);
                            } else {
                                selectEligibleSlots.parallelStream()
                                        .forEach(r -> sendMarkDownMessage(userContext.getChatId(), resultConverter(r)));
                            }
                        }
                    } catch (Exception e) {
                        log.error("Exception occurred for user flow", e);
                        sendMessage(userContext.getChatId(), "Error occurred for user flow");
                    }
                } else {
                    results.parallelStream()
                            .forEach(result -> sendMarkDownMessage(userContext.getChatId(), resultConverter(result)));
                }
            });
        }
    }

    private List<Result> selectEligibleSlots(UserContext userContext, List<Result> results) {
        if (null != userContext.getBeneficiarySelected()) {
            return results.stream().filter(r -> null != userContext.getBeneficiarySelected().getBirth_year()
                    && ((r.getAge() == 18 && 2021 - Integer.parseInt(userContext.getBeneficiarySelected().getBirth_year()) < 45)
                    || r.getAge() == 45 && 2021 - Integer.parseInt(userContext.getBeneficiarySelected().getBirth_year()) >= 45))
                    .filter(r -> (r.getDose1() > 0 && StringUtils.isEmpty(userContext.getBeneficiarySelected().getDose1_date()))
                            || (r.getDose2() > 0 && StringUtils.isNotEmpty(userContext.getBeneficiarySelected().getDose1_date())))
                    .collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public Boolean executeAction(Update update) {
        try {
            Message message = update.message();
            try {
                log.info(mapper.writeValueAsString(update));
            } catch (JsonProcessingException e) {
                log.info(update.toString(), e);
            }
            Long chatId;
            if (null != update.myChatMember()) {
                chatId = update.myChatMember().chat().id();
                sendMarkDownMessage(chatId, "Welcome to chat *" + update.myChatMember().from().firstName() + "*");
            } else {
                chatId = message.chat().id();
            }
            UserContext userContext = userContextMap.get(chatId);
            if (null == userContext) {
                userContext = new UserContext();
                userContextMap.put(chatId, userContext);
                userContext.setChatId(chatId);
            }

            if (null == update.myChatMember()) {
                stateMachineFlow(message, userContext);
            }
        } catch (
                Exception e) {
            log.error("Exception occurred while sending message", e);
            return false;
        }
        return true;
    }

    public void stateMachineFlow(Message message, UserContext userContext) throws IOException, TranscoderException {
        String text = null == message.text() ? Strings.EMPTY : message.text();
        if (null != message.entities() && Arrays.stream(message.entities())
                .anyMatch(entity -> MessageEntity.Type.bot_command.equals(entity.type()))) {
            commandFlow(userContext, text);
        } else if (userContext.getAction().startsWith("Command -")) {
            commandExecutionFlow(userContext, text);
        } else {
            userFlow(userContext, text, message.contact());
        }
    }

    public void commandExecutionFlow(UserContext userContext, String text) {
        String action = userContext.getPreviousAction();
        if (userContext.getAction().equalsIgnoreCase("Command - setpreferredslot")) {
            if ("09:00AM-11:00AM".equalsIgnoreCase(text)) {
                userContext.setDefaultSlot(1);
            } else if ("11:00AM-01:00PM".equalsIgnoreCase(text)) {
                userContext.setDefaultSlot(2);
            } else if ("01:00PM-03:00PM".equalsIgnoreCase(text)) {
                userContext.setDefaultSlot(3);
            } else if ("03:00PM-05:00PM".equalsIgnoreCase(text)) {
                userContext.setDefaultSlot(4);
            } else {
                userContext.setDefaultSlot(0);
            }
            sendMessage(userContext.getChatId(), "Slot set : " + text);
        }

        if (userContext.getAction().equalsIgnoreCase("Command - setPincode")) {
            if (text.matches("^[0-9]{1,6}$")) {
                sendMessage(userContext.getChatId(), "You will receive updates for pincode : " + text);
                userContext.setAction(userContext.getPreviousAction());
                if (StringUtils.isNotEmpty(userContext.getSelectedPincode())) {
                    Set<Long> registeredUsers = pinCodeRegistrationMap.get(userContext.getSelectedPincode());
                    if (null != registeredUsers) {
                        registeredUsers.remove(userContext.getChatId());
                    }
                }
                pinCodeRegistrationMap
                        .computeIfAbsent(text, k -> Collections.synchronizedSet(new HashSet<>()))
                        .add(userContext.getChatId());
                userContext.setSelectedPincode(text);
                userContext.setAction(userContext.getPreviousAction());
            } else {
                sendMessage(userContext.getChatId(), "Enter valid pincode of 6 digits");
            }
        }

        if (userContext.getAction().equalsIgnoreCase("Command - setDistrict")) {
            if (text.matches("^[0-9]+$")) {
                sendMessage(userContext.getChatId(), "You will receive updates for District : " + text);
                userContext.setAction(userContext.getPreviousAction());
                if (StringUtils.isNotEmpty(userContext.getSelectedDistrictId())) {
                    Set<Long> registeredUsers = districtRegistrationMap.get(userContext.getSelectedDistrictId());
                    if (null != registeredUsers) {
                        registeredUsers.remove(userContext.getChatId());
                    }
                }
                districtRegistrationMap
                        .computeIfAbsent(text, k -> Collections.synchronizedSet(new HashSet<>()))
                        .add(userContext.getChatId());
                userContext.setSelectedDistrictId(text);
                userContext.setAction(userContext.getPreviousAction());
            } else {
                sendMessage(userContext.getChatId(), "Enter numeric value only");
            }
        }

        if (userContext.getAction().equalsIgnoreCase("Command - enableAny")) {
            if (Boolean.TRUE.toString().equalsIgnoreCase(text)) {
                userContext.setSelectAnySlot(true);
                sendMessage(userContext.getChatId(), "Enable Any slot selection set to " + true);
            } else if (Boolean.FALSE.toString().equalsIgnoreCase(text)) {
                userContext.setSelectAnySlot(false);
                sendMessage(userContext.getChatId(), "Enable Any slot selection set to " + false);
            } else {
                sendMessage(userContext.getChatId(), "Enter true/false");
            }
        }

        if (userContext.getAction().equalsIgnoreCase("Command - selectVaccine")) {
            if ("COVAXIN".equalsIgnoreCase(text) || "COVISHIELD".equalsIgnoreCase(text)) {
                userContext.setPreferredVaccine(Collections.singletonList(text.toUpperCase()));
                sendMessage(userContext.getChatId(), "Vaccine selected " + text.toUpperCase());
            } else {
                userContext.setPreferredVaccine(null);
                sendMessage(userContext.getChatId(), "No preference selected");
            }
        }

        userContext.setAction(action);

    }

    private void commandFlow(UserContext userContext, String text) throws IOException, TranscoderException {
        String action = null;
        if (text.equalsIgnoreCase("/setpreferredslot")) {
            KeyboardButton[] keyboardButtons = {
                    new KeyboardButton("09:00AM-11:00AM"),
                    new KeyboardButton("11:00AM-01:00PM"),
                    new KeyboardButton("01:00PM-03:00PM"),
                    new KeyboardButton("03:00PM-05:00PM")};
            ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup(keyboardButtons);
            replyKeyboardMarkup.resizeKeyboard(true);
            replyKeyboardMarkup.oneTimeKeyboard(true);
            sendMessage(userContext.getChatId(), "Enter phone number", replyKeyboardMarkup);
            action = "Command - setpreferredslot";
        }
        if (text.equalsIgnoreCase("/setpincode")) {
            sendMessage(userContext.getChatId(), "Enter valid pincode :\ncurrent value is " + userContext.getSelectedPincode());
            action = "Command - setPincode";
        }
        if (text.equalsIgnoreCase("/setdistrict")) {
            sendMessage(userContext.getChatId(), "Enter valid district id :\ncurrent value is " + userContext.getSelectedDistrictId());
            action = "Command - setDistrict";
        }
        if (text.equalsIgnoreCase("/choosebeneficiary")) {
            userContext.setAction("Start journey");
            userFlow(userContext, Strings.EMPTY, null);
        }
        if (text.equalsIgnoreCase("/updatemobile")) {
            enterPhoneNo(userContext);
            userContext.setAction("Enter phone number");
            userContext.setEndAction("Enter Otp");
        }
        if (text.equalsIgnoreCase("/enableany")) {
            KeyboardButton[] keyboardButtons = {
                    new KeyboardButton("TRUE"),
                    new KeyboardButton("FALSE")};
            ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup(keyboardButtons);
            replyKeyboardMarkup.resizeKeyboard(true);
            replyKeyboardMarkup.oneTimeKeyboard(true);
            sendMessage(userContext.getChatId(), "Slots will be selected automatically once available," +
                    "do you want to enable/disable this feature ? \nCurrent preference :" + userContext.isSelectAnySlot(), replyKeyboardMarkup);
            action = "Command - enableAny";
        }
        if (text.equalsIgnoreCase("/selectvaccine")) {
            KeyboardButton[] keyboardButtons = {
                    new KeyboardButton("COVAXIN"),
                    new KeyboardButton("COVISHIELD"),
                    new KeyboardButton("No Preference")};
            ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup(keyboardButtons);
            replyKeyboardMarkup.resizeKeyboard(true);
            replyKeyboardMarkup.oneTimeKeyboard(true);
            sendMessage(userContext.getChatId(), "Choose Vaccine preference\nCurrent preference :" + userContext.getPreferredVaccine(), replyKeyboardMarkup);
            action = "Command - selectVaccine";
        }
        if (null != action) {
            userContext.setPreviousAction(userContext.getAction());
            userContext.setAction(action);
        }
    }

    public void userFlow(UserContext userContext, String text, Contact contact) throws IOException, TranscoderException {
        String action = null;
        if (text.equals("Nasheez")) {
            userContext.setAdmin(true);
            sendMessage(userContext.getChatId(), "Welcome admin, use below string in macrodroid");
            sendMessage(userContext.getChatId(), "\nhttps://cowinsms.herokuapp.com/postotp?userId=" + userContext.getChatId() + "&message=[sms_message]");
        }
        if (text.contains("SessionId")) {
            userContext.setAction(Strings.EMPTY);
            String[] tokens = text.split("\\$");
            userContext.setSessionId(tokens[tokens.length - 1]);
            userContext.setCenterId(tokens[tokens.length - 2]);
            userContext.setSlots(tokens[tokens.length - 3].split(","));
            if (userContext.getDefaultSlot() > 0 && userContext.getDefaultSlot() <= tokens.length) {
                userContext.setSelectedSlot(tokens[userContext.getDefaultSlot() - 1].substring(1));
            }
            userContext.setAction("Start journey");
        }
        if ("Start journey".equalsIgnoreCase(userContext.getAction())) {
            if (verifyToken(userContext.getToken(), 1)) {
                userContext.setAction("Choose Beneficiary");
            } else if (StringUtils.isNotEmpty(userContext.getMobile())) {
                sendOtp(userContext, userContext.getMobile(), true);
                action = "Enter Otp";
            } else {
                enterPhoneNo(userContext);
                action = "Enter phone number";
            }
        }
        if ("Enter phone number".equalsIgnoreCase(userContext.getAction())) {
            String mobile;
            if (null != contact) {
                mobile = contact.phoneNumber().substring(2);
            } else {
                mobile = text;
            }
            log.info(mobile);
            userContext.setMobile(mobile);
            sendOtp(userContext, mobile, true);
            action = "Enter Otp";
        }
        if ("Enter Otp".equalsIgnoreCase(userContext.getAction())) {
            VerifyOtpResponse otpResponse = verifyOtp(userContext, text);
            if (StringUtils.isEmpty(otpResponse.getErrorCode())
                    && StringUtils.isNotEmpty(otpResponse.getToken())) {
                userContext.setToken(otpResponse.getToken());
                sendMessage(userContext.getChatId(), "Authenticated");
                removeKeyboard(userContext);
                if ("Enter Otp".equalsIgnoreCase(userContext.getEndAction())) {
                    userContext.setAction(Strings.EMPTY);
                    userContext.setEndAction(Strings.EMPTY);
                    action = null;
                } else {
                    userContext.setAction("Choose Beneficiary");
                }
            } else {
                sendMessage(userContext.getChatId(), otpResponse.getError());
                sendMessage(userContext.getChatId(), "Enter otp again");
                action = "Enter Otp";
            }
        }
        if ("Choose Beneficiary".equalsIgnoreCase(userContext.getAction())) {
            List<BeneficiariesResponse.Beneficiary> beneficiaries = userContext.getBeneficiaries();
            if (CollectionUtils.isEmpty(beneficiaries)) {
                beneficiaries = cowin.getBeneficiaries(userContext.getToken()).getBody().getBeneficiaries();
                userContext.setBeneficiaries(beneficiaries);
            }
            if (beneficiaries.size() > 1) {
                ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup(beneficiaries
                        .stream().map(BeneficiariesResponse.Beneficiary::getName)
                        .map(KeyboardButton::new).toArray(KeyboardButton[]::new));
                replyKeyboardMarkup.resizeKeyboard(true);
                replyKeyboardMarkup.oneTimeKeyboard(true);
                sendMessage(userContext.getChatId(), "Select Beneficiary", replyKeyboardMarkup);
                action = "Select Beneficiary";
            } else {
                userContext.setBeneficiarySelected(beneficiaries.get(0));
                sendMessage(userContext.getChatId(), "Selected Beneficiary : " + beneficiaries.get(0).getName());
                userContext.setAction("Send Captcha");
            }
        }
        if ("Select Beneficiary".equalsIgnoreCase(userContext.getAction())) {
            Optional<BeneficiariesResponse.Beneficiary> optionalBeneficiary = userContext.getBeneficiaries().stream()
                    .filter(ben -> ben.getName().equalsIgnoreCase(text))
                    .findAny();
            if (optionalBeneficiary.isPresent()) {
                userContext.setBeneficiarySelected(optionalBeneficiary.get());
                userContext.setAction("Send Captcha");
            } else {
                sendMessage("Beneficiary not found");
                log.error("Beneficiary not found");
            }
        }
        if ("Send Captcha".equalsIgnoreCase(userContext.getAction())) {
            if (null != userContext.getBeneficiarySelected()
                    && StringUtils.isNotEmpty(userContext.getSessionId())) {
                sendCaptcha(userContext);
                action = "Enter Captcha";
            } else {
                userContext.setAction(Strings.EMPTY);
                action = null;
            }
        }
        if ("Enter Captcha".equalsIgnoreCase(userContext.getAction())) {
            userContext.setCaptcha(text);
            if (userContext.getDefaultSlot() > 0
                    && userContext.getDefaultSlot() <= userContext.getSlots().length) {
                userContext.setAction("Schedule Appointment");
            } else {
                userContext.setAction("Choose Slot");
            }
        }
        if ("Choose Slot".equalsIgnoreCase(userContext.getAction())
                && null != userContext.getBeneficiarySelected()
                && null != userContext.getCaptcha()) {
            ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup(
                    Arrays.stream(userContext.getSlots())
                            .map(KeyboardButton::new).toArray(KeyboardButton[]::new));
            replyKeyboardMarkup.resizeKeyboard(true);
            replyKeyboardMarkup.oneTimeKeyboard(true);
            sendMessage(userContext.getChatId(), "Select Slot", replyKeyboardMarkup);
            action = "Select Slot";
        }
        if ("Select Slot".equalsIgnoreCase(userContext.getAction())) {
            userContext.setSelectedSlot(text.substring(1));
            userContext.setAction("Schedule Appointment");
        }
        if ("Schedule Appointment".equalsIgnoreCase(userContext.getAction())
                && null != userContext.getBeneficiarySelected()
                && null != userContext.getCaptcha()) {
            ScheduleRequest scheduleRequest = new ScheduleRequest();
            scheduleRequest.setBeneficiaries(Collections.singletonList(userContext.getBeneficiarySelected().getBeneficiary_reference_id()));
            scheduleRequest.setCaptcha(userContext.getCaptcha());
            scheduleRequest.setDose(StringUtils.isEmpty(userContext.getBeneficiarySelected().getDose1_date()) ? 1 : 2);
            scheduleRequest.setSession_id(userContext.getSessionId());
            scheduleRequest.setSlot(userContext.getSelectedSlot().substring(1));
            scheduleRequest.setCenter_id(Integer.parseInt(userContext.getCenterId()));
            ScheduleResponse response = cowin.schedule(scheduleRequest, userContext.getToken()).getBody();
            if (null != response.getError()) {
                sendMessage(userContext.getChatId(), response.getError());
                if ("APPOIN0045".equalsIgnoreCase(response.getErrorCode())) {
                    sendCaptcha(userContext);
                    action = "Enter Captcha";
                } else {
                    userContext.setSessionId(Strings.EMPTY);
                    userContext.setAction(Strings.EMPTY);
                    action = null;
                }
            } else {
                sendMessage(userContext.getChatId(), "Booked Successfully with id " + response.getAppointment_id());
            }
        }

        if (null != action) {
            userContext.setAction(action);
        }
    }

    @Override
    public VerifyOtpResponse verifyOtp(UserContext userContext, String otp) throws IOException {
        VerifyOtpRequest verifyOtpRequest = new VerifyOtpRequest();
        verifyOtpRequest.setTxnId(userContext.getTxnId());
        verifyOtpRequest.setOtp(DigestUtils.sha256Hex(otp));
        VerifyOtpResponse otpResponse = cowin.verifyOTP(verifyOtpRequest).getBody();
        return otpResponse;
    }

    public void enterPhoneNo(UserContext userContext) {
        KeyboardButton keyboardButton = new KeyboardButton("Click here to share contact");
        keyboardButton.requestContact(true);
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup(keyboardButton);
        replyKeyboardMarkup.resizeKeyboard(true);
        replyKeyboardMarkup.oneTimeKeyboard(true);
        sendMessage(userContext.getChatId(), "Enter phone number", replyKeyboardMarkup);
    }

    public static boolean verifyToken(String token, Integer aheadTime) {
        if (StringUtils.isEmpty(token)) {
            return false;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, aheadTime);
        return JWT.decode(token).getExpiresAt().after(calendar.getTime());
    }

    @Override
    public void sendOtp(UserContext userContext, String mobile, boolean updateUser) throws IOException {
        GenerateOtpRequest generateOtpRequest = new GenerateOtpRequest();
        generateOtpRequest.setMobile(mobile);
        generateOtpRequest.setSecret("U2FsdGVkX1+Q9WSjibR49ZLvW1GLyoI4++gxOdFB2hqkj7drbStUz2n3Je3aBr7GaoRkHWPw9/nRY6y/pOLOQQ==");
        userContext.setTxnId(cowin.generateOTP(generateOtpRequest).getBody().getTxnId());
        Date currTime = new Date();
        log.info("user {} send time {}", userContext.getMobile(), currTime);
        userContext.setOtpSentTime(currTime.getTime());
        if (updateUser) {
            sendMessage(userContext.getChatId(), "Enter Otp");
        }
    }

    private void sendCaptcha(UserContext userContext) throws IOException, TranscoderException {
        CaptchaResponse response = cowin.getCaptcha(userContext.getToken()).getBody();
        telegramBot.execute(new SendPhoto(userContext.getChatId(), getCaptchaJpg(response.getCaptcha())));
        sendMessage(userContext.getChatId(), "Enter Captcha");
    }

    private byte[] getCaptchaJpg(String captcha) throws UnsupportedEncodingException, TranscoderException {
        InputStream stream = new ByteArrayInputStream(captcha.getBytes(StandardCharsets.UTF_8.name()));
        TranscoderInput input = new TranscoderInput(stream);

        JPEGTranscoder transcoder = new JPEGTranscoder();

        int RESOLUTION_DPI = 300;
        float SCALE_BY_RESOLUTION = RESOLUTION_DPI / 72f;
        float scaledWidth = 252 * SCALE_BY_RESOLUTION;
        float scaledHeight = 144 * SCALE_BY_RESOLUTION;
        float pixelUnitToMM = 25.4f / RESOLUTION_DPI;
        transcoder.addTranscodingHint(JPEGTranscoder.KEY_WIDTH, scaledWidth);
        transcoder.addTranscodingHint(JPEGTranscoder.KEY_HEIGHT, scaledHeight);
        transcoder.addTranscodingHint(JPEGTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, pixelUnitToMM);
        transcoder.addTranscodingHint(JPEGTranscoder.KEY_QUALITY, 1.0f);
        ByteArrayOutputStream ostream = new ByteArrayOutputStream();
        TranscoderOutput output = new TranscoderOutput(ostream);
        transcoder.transcode(input, output);
        return ostream.toByteArray();
    }

    @Override
    public SendResponse removeKeyboard(UserContext userContext) {
        return sendMessage(userContext.getChatId(), Strings.EMPTY, new ReplyKeyboardRemove());
    }

    @Override
    public SendResponse sendMessage(Long chatId, String message) {
        return telegramBot.execute(new SendMessage(chatId, message));
    }

    private SendResponse sendMarkDownMessage(Long chatId, String message) {
        SendMessage sendMessage = new SendMessage(chatId, message);
        sendMessage.parseMode(ParseMode.Markdown);
        return telegramBot.execute(sendMessage);
    }

    private SendResponse sendMessage(Long chatId, String message, Keyboard replyKeyboardMarkup) {
        SendMessage sendMessage = new SendMessage(chatId, message);
        sendMessage.replyMarkup(replyKeyboardMarkup);
        return telegramBot.execute(sendMessage);
    }

    @Override
    public SendResponse sendMessage(String message) {
        return telegramBot.execute(new SendMessage(chatId, message));
    }

    private String encrypt() throws NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException {
        String text = "b5cab167-7977-4df1-8027-a63aa144f04e";
        String secret = "CoWIN@$#&*(!@%^&\"";
        String cipherText = "U2FsdGVkX1+tsmZvCEFa/iGeSA0K7gvgs9KXeZKwbCDNCs2zPo+BXjvKYLrJutMK+hxTwl/hyaQLOaD7LLIRo2I5fyeRMPnroo6k8N9uwKk=";

        byte[] saltData = getRandomNonce(8);

        MessageDigest md5 = MessageDigest.getInstance("MD5");
        final byte[][] keyAndIV = GenerateKeyAndIV(32, 16, 1, saltData, secret.getBytes(UTF_8), md5);
        SecretKeySpec key = new SecretKeySpec(keyAndIV[0], "AES");
        IvParameterSpec iv = new IvParameterSpec(keyAndIV[1]);
        Cipher aesCBC = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aesCBC.init(Cipher.ENCRYPT_MODE, key, iv);

        byte[] cipherData = aesCBC.doFinal(text.getBytes(UTF_8));

        byte[] cipherTextWithIvSalt = ByteBuffer.allocate(keyAndIV[1].length + saltData.length + cipherData.length)
                .put(keyAndIV[1])
                .put(saltData)
                .put(cipherData)
                .array();

        // string representation, base64, send this string to other for decryption.
        String encryptedText = Base64.getEncoder().encodeToString(cipherTextWithIvSalt);

        System.out.println(encryptedText);

        return encryptedText;
    }

    public static byte[] getRandomNonce(int numBytes) {
        byte[] nonce = new byte[numBytes];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    public static byte[][] GenerateKeyAndIV(int keyLength, int ivLength, int iterations, byte[] salt, byte[] password, MessageDigest md) {

        int digestLength = md.getDigestLength();
        int requiredLength = (keyLength + ivLength + digestLength - 1) / digestLength * digestLength;
        byte[] generatedData = new byte[requiredLength];
        int generatedLength = 0;

        try {
            md.reset();

            // Repeat process until sufficient data has been generated
            while (generatedLength < keyLength + ivLength) {

                // Digest data (last digest if available, password data, salt if available)
                if (generatedLength > 0)
                    md.update(generatedData, generatedLength - digestLength, digestLength);
                md.update(password);
                if (salt != null)
                    md.update(salt, 0, 8);
                md.digest(generatedData, generatedLength, digestLength);

                // additional rounds
                for (int i = 1; i < iterations; i++) {
                    md.update(generatedData, generatedLength, digestLength);
                    md.digest(generatedData, generatedLength, digestLength);
                }

                generatedLength += digestLength;
            }

            // Copy key and IV into separate byte arrays
            byte[][] result = new byte[2][];
            result[0] = Arrays.copyOfRange(generatedData, 0, keyLength);
            if (ivLength > 0)
                result[1] = Arrays.copyOfRange(generatedData, keyLength, keyLength + ivLength);

            return result;

        } catch (DigestException e) {
            throw new RuntimeException(e);

        } finally {
            // Clean out temporary data
            Arrays.fill(generatedData, (byte) 0);
        }
    }
}
