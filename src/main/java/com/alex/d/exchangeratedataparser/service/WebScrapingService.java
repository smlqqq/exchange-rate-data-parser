package com.alex.d.exchangeratedataparser.service;

import com.alex.d.exchangeratedataparser.model.ExchangeRate;
import com.google.gson.JsonArray;
import org.springframework.stereotype.Service;

@Service
public interface WebScrapingService {
    JsonArray scrapeData();
    ExchangeRate getLatestExchangeRate();
    ExchangeRate checkAndUpdateLatestExchangeRate();
}
