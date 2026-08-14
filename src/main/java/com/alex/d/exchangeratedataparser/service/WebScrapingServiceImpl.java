package com.alex.d.exchangeratedataparser.service;

import com.alex.d.exchangeratedataparser.model.ExchangeRate;
import com.alex.d.exchangeratedataparser.model.ListItemClass;
import com.alex.d.exchangeratedataparser.repository.ExchangeRateRepository;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.java.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Log
public class WebScrapingServiceImpl implements WebScrapingService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final Gson gson = new Gson();

    public WebScrapingServiceImpl(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    // @Scheduled(cron = "0 0 12 * * *")
    @Scheduled(fixedRate = 600000) // каждые 10 минут
    @CacheEvict(value = "exchangeRatesCache", allEntries = true)
    public void scrapeAndSaveData() {
        JsonObject data = scrapeData();
        if (data != null && !data.has("error")) {
            log.info("Data obtained from scrapeData: " + data);
            exchangeRateRepository.saveWithCast(data.toString(), LocalDateTime.now().toString());
            log.info("Data successfully saved in db");
        } else {
            String error = data != null && data.has("error") ? data.get("error").getAsString() : "Unknown error";
            log.warning("Data scraping failed: " + error);
        }
    }

    @Cacheable(value = "exchangeRatesCache", key = "'data'")
    public JsonObject scrapeData() {
        JsonObject result = new JsonObject();
        JsonArray data = new JsonArray();
        try {
            Map<String, ListItemClass> mergedData = new HashMap<>();

            parseAndMerge(mergedData, "USD");
            parseAndMerge(mergedData, "EUR");
            parseAndMerge(mergedData, "RON");
            parseAndMerge(mergedData, "GBP");

            for (ListItemClass item : mergedData.values()) {
                JsonObject jsonItem = gson.toJsonTree(item).getAsJsonObject();
                data.add(jsonItem);
            }

            result.add("exchangeRates", data);
            result.addProperty("timestamp", LocalDateTime.now().toString());
            log.info("JSON created successfully with " + data.size() + " items.");

        } catch (Exception e) {
            log.severe("Error during data scraping: " + e.getMessage());
            result.addProperty("error", e.getMessage());
        }
        return result;
    }

    private void parseAndMerge(Map<String, ListItemClass> mergedData, String currency) throws Exception {
        String url = "https://valutar.md/ru?currency=" + currency;
        Document doc = Jsoup.connect(url).get();
        Elements tbody = doc.getElementsByTag("tbody");
        if (tbody.isEmpty()) {
            log.warning("No tbody elements found for " + currency);
            return;
        }

        Element ourTable = tbody.get(0);
        for (Element row : ourTable.children()) {
            if (row.children().size() < 3) continue;

            String bank = row.child(0).text().trim();
            if (bank.isEmpty() || bank.equalsIgnoreCase("Средний курс")) continue;

            String buy = row.child(1).text().trim();
            String sell = row.child(2).text().trim();

            ListItemClass item = mergedData.computeIfAbsent(bank, k -> {
                ListItemClass newItem = new ListItemClass();
                newItem.setBank(k);
                return newItem;
            });

            switch (currency) {
                case "USD":
                    item.setUsdB(buy);
                    item.setUsdS(sell);
                    break;
                case "EUR":
                    item.setEuroB(buy);
                    item.setEuroS(sell);
                    break;
                case "RON":
                    item.setRoLeuB(buy);
                    item.setRoLeuS(sell);
                    break;
                case "GBP":
                    item.setGbpB(buy);
                    item.setGbpS(sell);
                    break;
            }
        }
    }

    @Cacheable(value = "latestExchangeRates", key = "'latestData'")
    public ExchangeRate getLatestExchangeRate() {
        log.info("Fetching latestExchangeRate from cache or database");
        return exchangeRateRepository.findTopByOrderByTimestampDesc();
    }

    @Override
    public ExchangeRate checkAndUpdateLatestExchangeRate() {
        ExchangeRate latestExchangeRate = exchangeRateRepository.findTopByOrderByTimestampDesc();
        ExchangeRate cachedExchangeRate = getLatestExchangeRate();

        if (latestExchangeRate != null && (cachedExchangeRate == null || !latestExchangeRate.getTimestamp().equals(cachedExchangeRate.getTimestamp()))) {
            updateCache(latestExchangeRate);
        }
        return latestExchangeRate;
    }

    @CacheEvict(value = "latestExchangeRates", allEntries = true)
    public void evictCache() {
        log.info("Cache for latestExchangeRate evicted");
    }

    //@Scheduled(cron = "0 0 13 * * *")
    @Scheduled(fixedRate = 720000) // 720000 milliseconds = 12 minutes
    public void scheduledUpdateCache() {
        log.info("Scheduled update cache for latestExchangeRate");
        evictCache();
        checkAndUpdateLatestExchangeRate();
    }

    @CachePut(value = "latestExchangeRates", key = "'latestData'")
    public ExchangeRate updateCache(ExchangeRate exchangeRate) {
        log.info("Updating cache for latestExchangeRate with: " + exchangeRate);
        return exchangeRate;
    }
}
