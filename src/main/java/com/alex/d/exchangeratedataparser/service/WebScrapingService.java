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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@Service
@Log
public class WebScrapingService {

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    private String getCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

        @Scheduled(cron = "0 0 12 * * ?")
//    @Scheduled(cron = "*/10 * * * * *")
    public void scrapeAndSaveData() {
        scrapeData();
    }

    public JsonObject scrapeData() {
        JsonObject result = new JsonObject();
        JsonArray data = new JsonArray();
        try {
            Document doc = Jsoup.connect("https://valutar.md/ru").get();
            Elements tbody = doc.getElementsByTag("tbody");
            Element ourTable = tbody.get(0);

            for (int i = 0; i < 21; i++) {
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

                Gson gson = new Gson();
                JsonObject jsonItem = gson.toJsonTree(item).getAsJsonObject();
                data.add(jsonItem);
            }

            result.add("exchangeRates", data);
            result.addProperty("timestamp", getCurrentDate());

            log.info("JSON created successfully: " + result);

            ExchangeRate exchangeRate = new ExchangeRate();
            exchangeRate.setJsonData(result.toString());
            exchangeRate.setTimestamp(getCurrentDate());
            exchangeRateRepository.save(exchangeRate);

        } catch (Exception e) {
            e.printStackTrace();
            result.addProperty("error", e.getMessage()); // Handle error if needed
        }
        return result;
    }
}
