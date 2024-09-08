package com.alex.d.exchangeratedataparser.service;

import com.alex.d.exchangeratedataparser.model.ExchangeRate;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Service;

@Service
public interface WebScrapingService {
    JsonObject scrapeData();
    ExchangeRate getLatestExchangeRate();
    ExchangeRate checkAndUpdateLatestExchangeRate();
}
