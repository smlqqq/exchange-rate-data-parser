package com.alex.d.exchangeratedataparser.repository;

import com.alex.d.exchangeratedataparser.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO exchange_rate (json_data, timestamp) VALUES (CAST(:jsonData AS jsonb), :timestamp)", nativeQuery = true)
    void saveWithCast(@Param("jsonData") String jsonData, @Param("timestamp") String timestamp);
}
