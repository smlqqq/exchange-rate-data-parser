package com.alex.d.exchangeratedataparser.controller;


import com.alex.d.exchangeratedataparser.service.WebScrapingServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
public class WebScrapingController {

    private final WebScrapingServiceImpl webScrapingServiceImpl;


    public WebScrapingController(WebScrapingServiceImpl webScrapingServiceImpl) {
        this.webScrapingServiceImpl = webScrapingServiceImpl;

    }

    @GetMapping("/data/latest")
    public ResponseEntity<String> getLatestData() {
        try {
            JsonArray jsonArray = webScrapingServiceImpl.getLatestDataFromCache();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonArray.toString());
        } catch (Exception e) {
            log.error("Error fetching latest exchange rates", e);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("[]");
        }
    }
}
