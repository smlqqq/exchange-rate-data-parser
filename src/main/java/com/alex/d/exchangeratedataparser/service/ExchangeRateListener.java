package com.alex.d.exchangeratedataparser.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class ExchangeRateListener {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateListener.class);

    private final WebScrapingService webScrapingService;
    private final DataSource dataSource;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ExchangeRateListener(WebScrapingService webScrapingService, DataSource dataSource) {
        this.webScrapingService = webScrapingService;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void startListening() {
        executorService.submit(this::listenForExchangeRateUpdates);
    }

    private void listenForExchangeRateUpdates() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("LISTEN exchange_rate_updated");

            while (!Thread.currentThread().isInterrupted()) {
                // Poll for notifications
                org.postgresql.PGNotification[] notifications = ((org.postgresql.PGConnection) connection).getNotifications();
                if (notifications != null) {
                    for (org.postgresql.PGNotification notification : notifications) {
                        if ("exchange_rate_updated".equals(notification.getName())) {
                            log.info("Received notification: exchange_rate_updated");
                            webScrapingService.checkAndUpdateLatestExchangeRate();
                        }
                    }
                }
                // Sleep to prevent busy-waiting
                Thread.sleep(1000);
            }

        } catch (SQLException | InterruptedException e) {
            log.error("Error while listening for exchange rate updates", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
