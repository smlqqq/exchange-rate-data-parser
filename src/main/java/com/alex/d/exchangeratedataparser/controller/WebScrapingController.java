package com.alex.d.exchangeratedataparser.controller;


import com.alex.d.exchangeratedataparser.service.WebScrapingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebScrapingController {

    private final WebScrapingService webScrapingService;

    public WebScrapingController(WebScrapingService webScrapingService) {
        this.webScrapingService = webScrapingService;
    }


    @GetMapping("/api/data")
    public Object getData() {
        return webScrapingService.scrapeData();
    }
}
