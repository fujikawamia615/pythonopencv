package com.xiaoxi.facegate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final RedisState redis;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String adminUsername;
    private final String adminPassword;

    public AuthController(JdbcTemplate jdbc, RedisState redis,
        @Value("${app.admin-username}") String username, @Value("${app.admin-password}") String password) {
        this.jdbc = jdbc; this.redis = redis;
        this.adminUsername = username; this.adminPassword = password;
    }
    @Override
    public void run(ApplicationArguments args) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM admin_user", Long.class) == 0) {
            if (adminPassword.length() < 12 || adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalStateException("ADMIN_PASSWORD must contain 12-72 UTF-8 bytes");
            jdbc.update("INSERT INTO admin_user(username,password_hash) VALUES (?,?)", adminUsername, encoder.encode(adminPassword));
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM door", Long.class) == 0)
            jdbc.update("INSERT INTO door(name,location,enabled) VALUES (?,?,?)", "实验室入口", "教学楼 A 座 301", true);
    }
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody Models.Login input, HttpServletRequest request) {
        if (!redis.allowLogin(request.getRemoteAddr())) throw new ApiException(429, "登录尝试过多，请 15 分钟后重试");
        var hashes = jdbc.queryForList("SELECT password_hash FROM admin_user WHERE username=?", String.class, input.username());
        // BCrypt accepts at most 72 bytes; oversized passwords must be rejected before encoding.
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || hashes.isEmpty()
            || !encoder.matches(input.password(), hashes.get(0))) throw new ApiException(401, "用户名或密码错误");
        return Map.of("token", redis.createSession(input.username()), "username", input.username(), "expiresIn", 28800);
    }
    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest request) {
        redis.logout(AuthConfig.token(request));
        return Map.of("success", true);
    }
}
