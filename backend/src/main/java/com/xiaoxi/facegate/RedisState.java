package com.xiaoxi.facegate;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class RedisState {
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();
    public RedisState(StringRedisTemplate redis) { this.redis = redis; }

    public String createSession(String username) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set("session:" + token, username, Duration.ofHours(8));
        return token;
    }
    public String session(String token) { return redis.opsForValue().get("session:" + token); }
    public void logout(String token) { redis.delete("session:" + token); }

    public boolean allowLogin(String ip) {
        String key = "login-attempt:" + ip;
        Long count = redis.opsForValue().increment(key);
        if (Long.valueOf(1).equals(count)) redis.expire(key, Duration.ofMinutes(15));
        return count != null && count <= 30;
    }
    public boolean acquireDoor(long personId, long doorId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
            "door-cooldown:" + personId + ":" + doorId, "1", Duration.ofSeconds(5)));
    }
}
