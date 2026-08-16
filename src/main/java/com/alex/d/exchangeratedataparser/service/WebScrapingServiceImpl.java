package com.alex.d.exchangeratedataparser.service;

import com.alex.d.exchangeratedataparser.model.ExchangeRate;
import com.alex.d.exchangeratedataparser.model.ListItemClass;
import com.alex.d.exchangeratedataparser.repository.ExchangeRateRepository;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class WebScrapingServiceImpl implements WebScrapingService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Gson gson = new Gson();

    public WebScrapingServiceImpl(ExchangeRateRepository exchangeRateRepository, RedisTemplate<String, Object> redisTemplate) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.redisTemplate = redisTemplate;
    }

    // @Scheduled(cron = "0 0 12 * * *")  // каждый день в 12:00
    @Scheduled(fixedRate = 600000) // каждые 10 минут
    @CacheEvict(value = "exchangeRatesCache", allEntries = true)
    public void scrapeAndSaveData() {
        JsonArray data = scrapeData().getAsJsonArray();
        if (data != null && !data.isEmpty()) {
            log.info("Data obtained from scrapeData: " + data);

            exchangeRateRepository.saveWithCast(data.toString(), LocalDateTime.now().toString());
            log.info("Data successfully saved in db");

            updateRedisCache(data);
        } else {
            log.warn("Data scraping failed");
        }
    }

    public void updateRedisCache(JsonArray exchangeRatesData) {
        log.info("Updating Redis cache with new exchange rates data");
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        valueOperations.set("exchangeRates", exchangeRatesData.toString());
    }

    @Cacheable(value = "exchangeRatesCache", key = "'data'")
    public JsonArray scrapeData() {
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
                log.warn("Unexpected number of rows in the table. Expected: " + expectedRowCount + ", Found: " + ourTable.children().size());
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
            log.info("JSON created successfully with " + data.size() + " items.");
        } catch (Exception e) {
            log.error("Error during data scraping: ", e);
        }
        return data;
    }


    @Override
    public ExchangeRate getLatestExchangeRate() {
        return null;
    }


    public JsonArray getLatestDataFromCache() {
        log.info("Fetching exchange rates from Redis cache");
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        String data = (String) valueOperations.get("exchangeRates");
        if (data != null) {
            return gson.fromJson(data, JsonArray.class);
        } else
            log.warn("No exchange rates found in Redis cache");
        JsonArray exchangeRatesData = scrapeData();
        return gson.fromJson(exchangeRatesData, JsonArray.class);
    }

    @Override
    public ExchangeRate checkAndUpdateLatestExchangeRate() {
        JsonArray latestData = getLatestDataFromCache();

        if (latestData != null) {
            log.info("Latest data fetched from Redis cache");
            return null;
        }

        log.info("No data found in cache, fetching from database");
        return exchangeRateRepository.findTopByOrderByTimestampDesc();
    }

    @Scheduled(fixedRate = 720000) // каждые 12 минут
    public void scheduledUpdateCache() {
        log.info("Scheduled update cache for exchange rates");
        scrapeAndSaveData();
    }

}
