package com.alex.d.exchangeratedataparser.service;

import com.alex.d.exchangeratedataparser.model.ListItemClass;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
public class WebScrapingService {

    private String getCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    public List<ListItemClass> scrapeData() {
        List<ListItemClass> listItems = new ArrayList<>();
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
                item.setDate(getCurrentDate());
                listItems.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return listItems;
    }
}
