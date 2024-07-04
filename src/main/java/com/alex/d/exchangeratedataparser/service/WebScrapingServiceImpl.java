package com.alex.d.exchangeratedataparser.service;

import com.alex.d.exchangeratedataparser.model.ExchangeRate;
import com.google.gson.JsonObject;

public interface WebScrapingServiceImpl {
    JsonObject scrapeData();
    ExchangeRate getLatestExchangeRate();
}
