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
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class WebScrapingServiceImpl implements WebScrapingService {

    private static final String BASE_URL = "https://valutar.md";
    private static final String BANKS_LIST_URL = BASE_URL + "/ru/banks";

    // Currency names exactly as they appear in the "Валюта" column on a bank's
    // page (ru locale). We match by "contains" so minor label differences
    // (e.g. trailing notes) don't break matching.
    private static final String CUR_USD = "Доллар США";
    private static final String CUR_EUR = "Евро";
    private static final String CUR_RON = "Румынский лей";
    private static final String CUR_GBP = "Фунт стерлингов";

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

    /**
     * IMPORTANT: valutar.md changed its markup. There is no longer a single
     * "tbody with 21 rows / fixed column indices" table that lists every
     * bank x currency pair. Instead:
     *
     *  - https://valutar.md/ru/curs shows a "Организация | покупка | продажа"
     *    table, but only for ONE currency at a time (selected via a dropdown
     *    filter) — not useful for a single-pass multi-currency scrape.
     *  - https://valutar.md/ru/banks/{slug} shows a "Валюта | покупка | продажа"
     *    table with EVERY currency for that ONE bank.
     *
     * So we now: 1) discover the current list of bank pages from
     * /ru/banks, 2) visit each bank page and pick out the currencies we
     * need BY NAME instead of by column position (fixed indices are exactly
     * what broke last time and will break again the next time the site
     * reorders or adds a column).
     *
     * NOTE: I could not inspect the live raw HTML/CSS classes directly (only
     * a cleaned text/markdown extraction of the page was available to me),
     * so table/row selectors below are written defensively (by header text
     * and by currency name) rather than assuming specific class names.
     * Please sanity-check against the real page once and adjust the
     * selectors in fetchBankLinks()/scrapeBankPage() if needed.
     */
    @Cacheable(value = "exchangeRatesCache", key = "'data'")
    public JsonArray scrapeData() {
        JsonArray data = new JsonArray();
        try {
            Map<String, String> bankLinks = fetchBankLinks();

            if (bankLinks.isEmpty()) {
                throw new IllegalStateException("No bank links found on " + BANKS_LIST_URL);
            }

            for (Map.Entry<String, String> entry : bankLinks.entrySet()) {
                String bankName = entry.getKey();
                String bankUrl = entry.getValue();
                try {
                    ListItemClass item = scrapeBankPage(bankName, bankUrl);
                    if (item != null) {
                        JsonObject jsonItem = gson.toJsonTree(item).getAsJsonObject();
                        data.add(jsonItem);
                    }
                } catch (Exception e) {
                    log.error("Error scraping bank page {} ({}): {}", bankName, bankUrl, e.getMessage());
                }
            }
            log.info("JSON created successfully with " + data.size() + " items.");
        } catch (Exception e) {
            log.error("Error during data scraping: ", e);
        }
        return data;
    }

    /**
     * Reads https://valutar.md/ru/banks and returns bank name -> bank page URL,
     * e.g. "Banca Națională" -> "https://valutar.md/ru/banks/banca-nationala".
     * We look for links whose href matches the /banks/{slug} pattern rather
     * than hardcoding the list of banks, so new/removed banks are picked up
     * automatically.
     */
    private Map<String, String> fetchBankLinks() throws Exception {
        Document doc = Jsoup.connect(BANKS_LIST_URL)
                .userAgent("Mozilla/5.0 (compatible; ExchangeRateBot/1.0)")
                .get();

        Map<String, String> hrefToName = new LinkedHashMap<>();
        for (Element a : doc.select("a[href*=/banks/]")) {
            String href = a.attr("abs:href");
            String text = a.text().trim();

            if (!href.matches(".*/banks/[a-z0-9\\-]+/?$")) continue;
            if (text.isEmpty() || text.equalsIgnoreCase("Подробнее")) continue;

            // dedupe by href, not by text — a bank can have both a name link
            // and a "read more" link pointing at the same page
            hrefToName.putIfAbsent(href, text);
        }

        Map<String, String> nameToHref = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : hrefToName.entrySet()) {
            nameToHref.put(e.getValue(), e.getKey());
        }
        return nameToHref;
    }

    private ListItemClass scrapeBankPage(String bankName, String bankUrl) throws Exception {
        Document doc = Jsoup.connect(bankUrl)
                .userAgent("Mozilla/5.0 (compatible; ExchangeRateBot/1.0)")
                .get();

        // Find the rates table: the one whose text mentions both "покупка" and "продажа".
        Element table = null;
        for (Element candidate : doc.select("table")) {
            String headText = candidate.text().toLowerCase();
            if (headText.contains("покупка") && headText.contains("продажа")) {
                table = candidate;
                break;
            }
        }
        if (table == null) {
            log.warn("No rate table found for bank {} at {}", bankName, bankUrl);
            return null;
        }

        ListItemClass item = new ListItemClass();
        item.setBank(bankName);

        for (Element row : table.select("tr")) {
            // skip header row(s)
            if (!row.select("th").isEmpty()) continue;

            Elements cells = row.select("td");
            if (cells.size() < 3) continue;

            String currencyName = cells.get(0).text().trim();
            String buy = cells.get(1).text().trim();
            String sell = cells.get(2).text().trim();

            if (currencyName.contains(CUR_USD)) {
                item.setUsdB(buy);
                item.setUsdS(sell);
            } else if (currencyName.contains(CUR_EUR)) {
                item.setEuroB(buy);
                item.setEuroS(sell);
            } else if (currencyName.contains(CUR_RON)) {
                item.setRoLeuB(buy);
                item.setRoLeuS(sell);
            } else if (currencyName.contains(CUR_GBP)) {
                item.setGbpB(buy);
                item.setGbpS(sell);
            }
        }
        return item;
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