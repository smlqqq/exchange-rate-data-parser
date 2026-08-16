package com.alex.d.exchangeratedataparser.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public String checkConnection() {
        try {
            String response = redisTemplate.getConnectionFactory().getConnection().ping();
            return "Redis Connection Successful: " + response;
        } catch (Exception e) {
            return "Redis Connection Failed: " + e.getMessage();
        }
    }
}
