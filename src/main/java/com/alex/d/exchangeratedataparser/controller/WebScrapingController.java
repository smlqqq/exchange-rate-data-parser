package com.alex.d.exchangeratedataparser.controller;


import com.alex.d.exchangeratedataparser.service.WebScrapingServiceImpl;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
public class WebScrapingController {
    private final Gson gson = new Gson();

    private final WebScrapingServiceImpl webScrapingServiceImpl;


    public WebScrapingController(WebScrapingServiceImpl webScrapingServiceImpl) {
        this.webScrapingServiceImpl = webScrapingServiceImpl;

    }

    //    TODO Разобраться с Сериализацией json объекта
//    @GetMapping("/data")
//    public JsonObject getData() {
//        log.info("Fetching data from scrapeData method");
//        return webScrapingServiceImpl.scrapeData();
//    }


//
//    @GetMapping("/data/latest")
//    public JsonArray getLatestData() {
//        ExchangeRate latestExchangeRate = webScrapingServiceImpl.checkAndUpdateLatestExchangeRate();
//        if (latestExchangeRate == null) {
//            log.warning("No latest exchange rate found in cache");
//            return new JsonArray();
//        }
//
//        try {
//            JsonArray jsonArray = gson.fromJson(latestExchangeRate.getJsonData(), JsonArray.class);
//            log.info("Latest data from cache parsed successfully");
//            return jsonArray;
//        } catch (JsonSyntaxException e) {
//            log.severe("Failed to parse JSON data: " + e.getMessage());
//            return new JsonArray();
//        }
//    }


    @GetMapping("/data/latest")
    public JsonArray getLatestData() {
        try {
            JsonArray jsonArray = webScrapingServiceImpl.getLatestDataFromCache();
            log.info("Returning latest exchange rates data from cache");
            return jsonArray;
        } catch (Exception e) {
            log.error("Error fetching latest exchange rates", e);
            return new JsonArray();
        }
    }
}
