package com.xiaoxi.facegate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Validated
@RestController
@RequestMapping("/api")
public class GateController {
    private final GateRepository repo;
    private final GateService service;
    private final FaceClient face;
    public GateController(GateRepository repo, GateService service, FaceClient face) {
        this.repo = repo; this.service = service; this.face = face;
    }
    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "ok"); }
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        var stats = repo.stats(service.now().toLocalDate().atStartOfDay());
        stats.put("faceServiceReady", face.healthy());
        stats.put("serverTime", service.now());
        stats.put("reasons", GateService.REASONS);
        return stats;
    }
    @GetMapping("/persons")
    public Models.Page<Models.Person> persons(@RequestParam(defaultValue="") @Size(max=80) String search,
        @RequestParam(defaultValue="1") @Min(1) @Max(100000) int page,
        @RequestParam(defaultValue="10") @Min(1) @Max(100) int size) {
        return repo.persons(search, page, size);
    }
    @PostMapping("/persons")
    public Models.Person createPerson(@Valid @RequestBody Models.PersonInput input) { return repo.person(repo.createPerson(input)); }
    @PutMapping("/persons/{id}")
    public Models.Person updatePerson(@PathVariable @Positive long id, @Valid @RequestBody Models.PersonInput input) {
        repo.updatePerson(id, input); return repo.person(id);
    }
    @DeleteMapping("/persons/{id}")
    public Map<String, Boolean> deletePerson(@PathVariable @Positive long id) { repo.deletePerson(id); return ok(); }
    @PostMapping("/persons/{id}/face")
    public Models.Person enroll(@PathVariable @Positive long id, @RequestPart MultipartFile file) {
        service.enroll(id, file); return repo.person(id);
    }
    @DeleteMapping("/persons/{id}/face")
    public Map<String, Boolean> removeFace(@PathVariable @Positive long id) { repo.removeFace(id); return ok(); }
    @GetMapping("/doors")
    public List<Models.Door> doors() { return repo.doors(); }
    @PostMapping("/doors")
    public Models.Door createDoor(@Valid @RequestBody Models.DoorInput input) { return repo.door(repo.createDoor(input)); }
    @PutMapping("/doors/{id}")
    public Models.Door updateDoor(@PathVariable @Positive long id, @Valid @RequestBody Models.DoorInput input) {
        repo.updateDoor(id, input); return repo.door(id);
    }
    @GetMapping("/grants")
    public List<Models.Grant> grants() { return repo.grants(); }
    @PostMapping("/grants")
    public Map<String, Long> saveGrant(@Valid @RequestBody Models.GrantInput input) { return Map.of("id", repo.saveGrant(input)); }
    @DeleteMapping("/grants/{id}")
    public Map<String, Boolean> deleteGrant(@PathVariable @Positive long id) { repo.deleteGrant(id); return ok(); }
    @PostMapping("/access/recognize")
    public Models.ScanResult scan(@RequestParam @Positive long doorId, @RequestPart MultipartFile file) { return service.scan(doorId, file); }
    @GetMapping("/logs")
    public Models.Page<Models.Log> logs(@RequestParam(defaultValue="") @Size(max=80) String search,
        @RequestParam(required=false) Boolean allowed,
        @RequestParam(defaultValue="1") @Min(1) @Max(100000) int page,
        @RequestParam(defaultValue="10") @Min(1) @Max(100) int size) {
        return repo.logs(search, allowed, page, size);
    }
    private Map<String, Boolean> ok() { return Map.of("success", true); }
}
