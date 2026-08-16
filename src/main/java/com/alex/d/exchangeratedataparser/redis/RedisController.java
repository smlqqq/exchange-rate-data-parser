package com.alex.d.exchangeratedataparser.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedisController {

    @Autowired
    private RedisService redisService;

    @GetMapping("/check-redis")
    public String checkRedisConnection() {
        return redisService.checkConnection();
    }
}
