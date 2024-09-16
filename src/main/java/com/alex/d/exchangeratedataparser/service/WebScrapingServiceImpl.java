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

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;

@Service
@Log
public class WebScrapingServiceImpl implements WebScrapingService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final Gson gson = new Gson();

    public WebScrapingServiceImpl(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    ZoneId chisinau = ZoneId.of("Europe/Chisinau");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(chisinau);

//    @Scheduled(cron = "0 0 12 * * *")
    @Scheduled(cron = "*/10 * * * *")
    @CacheEvict(value = "exchangeRatesCache", allEntries = true)
    public void scrapeAndSaveData() {
        JsonObject data = scrapeData();
        if (data != null && !data.has("error")) {
            log.info("Data obtained from scrapeData: " + data);
            exchangeRateRepository.saveWithCast(data.toString(), zonedDateTime.toString());
            log.info("Data successfully saved in db");
        } else {
            log.warning("Data scraping failed: " + data.get("error").getAsString());
        }
    }

    @Cacheable(value = "exchangeRatesCache", key = "'data'")
    public JsonObject scrapeData() {
        JsonObject result = new JsonObject();
        JsonArray data = new JsonArray();
        try {
            Document doc = Jsoup.connect("https://valutar.md/ru").get();
            Elements tbody = doc.getElementsByTag("tbody");
            if (tbody.isEmpty()) {
                throw new IllegalStateException("No tbody elements found on the page.");
            }
            Element ourTable = tbody.get(0);
            int expectedRowCount = 21;
            if (ourTable.children().size() < expectedRowCount) {
                log.warning("Unexpected number of rows in the table. Expected: " + expectedRowCount + ", Found: " + ourTable.children().size());
            }

            for (int i = 0; i < Math.min(expectedRowCount, ourTable.children().size()); i++) {
                ListItemClass item = new ListItemClass();
                item.setBank(ourTable.children().get(i).child(0).text());
                item.setUsdB(ourTable.children().get(i).child(1).text());
                item.setUsdS(ourTable.children().get(i).child(2).text());
                item.setEuroB(ourTable.children().get(i).child(3).text());
                item.setEuroS(ourTable.children().get(i).child(4).text());
                item.setRoLeuB(ourTable.children().get(i).child(7).text());
                item.setRoLeuS(ourTable.children().get(i).child(8).text());
                item.setGbpB(ourTable.children().get(i).child(11).text());
                item.setGbpS(ourTable.children().get(i).child(12).text());

                JsonObject jsonItem = gson.toJsonTree(item).getAsJsonObject();
                data.add(jsonItem);
            }

            result.add("exchangeRates", data);
            result.addProperty("timestamp", zonedDateTime.toString());
            log.info("JSON created successfully with " + data.size() + " items.");

        } catch (Exception e) {
            log.severe("Error during data scraping: " + e.getMessage());
            result.addProperty("error", e.getMessage());
        }
        return result;
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

//    @Scheduled(cron = "0 0 13 * * *") // Если требуется периодическое обновление кеша
    @Scheduled(cron = "*/12 * * * *") // Если требуется периодическое обновление кеша
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
