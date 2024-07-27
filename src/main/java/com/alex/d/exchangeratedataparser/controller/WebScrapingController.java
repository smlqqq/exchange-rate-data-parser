package com.alex.d.exchangeratedataparser.controller;


import com.alex.d.exchangeratedataparser.model.ExchangeRate;
import com.alex.d.exchangeratedataparser.service.WebScrapingService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.java.Log;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log
@RestController
@RequestMapping("/api")
public class WebScrapingController {
    private final Gson gson = new Gson();

    private final WebScrapingService webScrapingService;


    public WebScrapingController(WebScrapingService webScrapingService) {
        this.webScrapingService = webScrapingService;

    }

    @GetMapping("/data")
    public Object getData() {
        log.info("Data saved to database");
        return webScrapingService.scrapeData();
    }

    @GetMapping("/data/latest")
    public JsonObject getLatestData() {
        ExchangeRate latestExchangeRate = webScrapingService.checkAndUpdateLatestExchangeRate();
        if (latestExchangeRate == null) {
            log.warning("No latest exchange rate found in cache");
            return new JsonObject(); // Возвращаем пустой JSON, если данные не найдены
        }
        JsonObject jsonObject = gson.fromJson(latestExchangeRate.getJsonData(), JsonObject.class);
        log.info("Latest data from cache parsed successfully");
        return jsonObject;
    }
}
