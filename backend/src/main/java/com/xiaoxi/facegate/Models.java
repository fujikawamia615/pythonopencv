package com.xiaoxi.facegate;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class Models {
    private Models() {}
    public record Login(@NotBlank @Size(max=50) String username,
                        @NotBlank @Size(max=100) String password) {}
    public record PersonInput(@NotBlank @Size(max=40) String personNo,
                              @NotBlank @Size(max=50) String name,
                              @NotNull @Size(max=80) String department,
                              @NotNull Boolean enabled) {}
    public record DoorInput(@NotBlank @Size(max=80) String name,
                            @NotBlank @Size(max=120) String location,
                            @NotNull Boolean enabled) {}
    public record GrantInput(@NotNull @Positive Long personId, @NotNull @Positive Long doorId,
                             @NotNull LocalDateTime validFrom, @NotNull LocalDateTime validUntil) {}
    public record Person(long id, String personNo, String name, String department,
                         boolean enabled, boolean enrolled, LocalDateTime enrolledAt) {}
    public record Door(long id, String name, String location, boolean enabled) {}
    public record Grant(long id, long personId, String personName, String personNo,
                        long doorId, String doorName, LocalDateTime validFrom, LocalDateTime validUntil) {}
    public record Log(long id, Long personId, String personName, String personNo, long doorId,
                      String doorName, boolean allowed, String reason, Double score, LocalDateTime createdAt) {}
    public record Page<T>(List<T> items, long total, int page, int size) {}
    public record Feature(List<Double> feature, String model, List<Integer> box) {}
    public record Candidate(long id, List<Double> feature) {}
    public record Match(Long personId, double score, double runnerUpScore, boolean ambiguous) {}
    public record MatchInput(List<Double> feature, List<Candidate> gallery, double threshold, double margin) {}
    public record ScanResult(boolean allowed, boolean doorOpen, String reason, String message,
                             Long personId, String personName, double score, long logId) {}
}
