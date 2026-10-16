package com.xiaoxi.facegate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.Map;

@Service
public class GateService {
    private final GateRepository repo;
    private final FaceClient face;
    private final RedisState redis;
    private final double threshold, margin;
    private final ZoneId zone;
    public static final Map<String, String> REASONS = Map.ofEntries(
        Map.entry("ALLOWED", "识别成功，模拟开门 5 秒"),
        Map.entry("UNKNOWN_FACE", "未识别到已录入人员"),
        Map.entry("AMBIGUOUS_FACE", "匹配结果接近，请更换清晰照片"),
        Map.entry("PERSON_DISABLED", "该人员已被禁用"),
        Map.entry("DOOR_DISABLED", "该门禁已停用"),
        Map.entry("NO_PERMISSION", "没有该门禁的有效通行权限"),
        Map.entry("COOLDOWN", "请勿重复通行，5 秒后重试"),
        Map.entry("INVALID_IMAGE", "图片中必须包含一张清晰人脸"),
        Map.entry("FACE_UNAVAILABLE", "识别服务暂时不可用")
    );
    public GateService(GateRepository repo, FaceClient face, RedisState redis,
        @Value("${app.threshold}") double threshold, @Value("${app.margin}") double margin,
        @Value("${app.zone}") String zone) {
        if (threshold < 0 || threshold > 1 || margin < 0 || margin > 1) throw new IllegalArgumentException("Invalid face threshold/margin");
        this.repo = repo; this.face = face; this.redis = redis;
        this.threshold = threshold; this.margin = margin; this.zone = ZoneId.of(zone);
    }
    public void enroll(long id, MultipartFile file) {
        repo.person(id);
        var feature = face.extract(file);
        var others = repo.gallery().stream().filter(c -> c.id() != id).toList();
        if (!others.isEmpty()) {
            var match = face.match(feature.feature(), others, threshold, 0);
            if (match.personId() != null) throw new ApiException(409, "该人脸与已有人员匹配，请检查是否重复录入");
        }
        repo.enroll(id, feature.feature(), now());
    }
    public Models.ScanResult scan(long doorId, MultipartFile file) {
        Models.Door door = repo.door(doorId);
        if (!door.enabled()) return decision(null, door, "DOOR_DISABLED", 0);
        Models.Match match;
        try {
            var feature = face.extract(file);
            match = face.match(feature.feature(), repo.gallery(), threshold, margin);
        } catch (ApiException e) {
            if (e.status() == 400 || e.status() == 422) return decision(null, door, "INVALID_IMAGE", 0);
            if (e.status() == 503) return decision(null, door, "FACE_UNAVAILABLE", 0);
            throw e;
        }
        if (match.personId() == null) return decision(null, door, match.ambiguous() ? "AMBIGUOUS_FACE" : "UNKNOWN_FACE", match.score());
        Models.Person person = repo.person(match.personId());
        if (!person.enabled()) return decision(person, door, "PERSON_DISABLED", match.score());
        if (!repo.authorized(person.id(), door.id(), now())) return decision(person, door, "NO_PERMISSION", match.score());
        if (!redis.acquireDoor(person.id(), door.id())) return decision(person, door, "COOLDOWN", match.score());
        return decision(person, door, "ALLOWED", match.score());
    }
    private Models.ScanResult decision(Models.Person person, Models.Door door, String reason, double score) {
        boolean allowed = reason.equals("ALLOWED");
        long logId = repo.log(person, door, allowed, reason, score, now());
        return new Models.ScanResult(allowed, allowed, reason, REASONS.get(reason),
            person == null ? null : person.id(), person == null ? null : person.name(), score, logId);
    }
    public LocalDateTime now() { return LocalDateTime.now(zone); }
}
