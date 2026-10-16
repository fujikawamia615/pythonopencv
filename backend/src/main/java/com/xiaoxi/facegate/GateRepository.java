package com.xiaoxi.facegate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class GateRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public GateRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
    private Models.Person mapPerson(ResultSet rs, int row) throws SQLException {
        return new Models.Person(rs.getLong("id"), rs.getString("person_no"), rs.getString("name"),
            rs.getString("department"), rs.getBoolean("enabled"), rs.getString("feature") != null, time(rs, "enrolled_at"));
    }
    public Models.Person person(long id) {
        return jdbc.query("SELECT * FROM person WHERE id=?", this::mapPerson, id).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "人员不存在"));
    }
    public Models.Page<Models.Person> persons(String search, int page, int size) {
        String term = "%" + search + "%";
        String where = " FROM person WHERE name LIKE ? OR person_no LIKE ? OR department LIKE ?";
        var items = jdbc.query("SELECT *" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", this::mapPerson,
            term, term, term, size, (page - 1) * size);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, term, term, term);
        return new Models.Page<>(items, total, page, size);
    }
    public long createPerson(Models.PersonInput input) {
        return insert("INSERT INTO person(person_no,name,department,enabled) VALUES (?,?,?,?)",
            input.personNo().trim(), input.name().trim(), input.department().trim(), input.enabled());
    }
    public void updatePerson(long id, Models.PersonInput input) {
        person(id);
        jdbc.update("UPDATE person SET person_no=?,name=?,department=?,enabled=? WHERE id=?",
            input.personNo().trim(), input.name().trim(), input.department().trim(), input.enabled(), id);
    }
    public void deletePerson(long id) {
        if (jdbc.update("DELETE FROM person WHERE id=?", id) == 0) throw new ApiException(404, "人员不存在");
    }
    public void enroll(long id, List<Double> feature, LocalDateTime now) {
        try { jdbc.update("UPDATE person SET feature=?,enrolled_at=? WHERE id=?", json.writeValueAsString(feature), now, id); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    public void removeFace(long id) {
        person(id);
        jdbc.update("UPDATE person SET feature=NULL,enrolled_at=NULL WHERE id=?", id);
    }
    public List<Models.Candidate> gallery() {
        return jdbc.query("SELECT id,feature FROM person WHERE feature IS NOT NULL", (rs, row) -> {
            try {
                return new Models.Candidate(rs.getLong("id"), json.readValue(rs.getString("feature"), new TypeReference<List<Double>>() {}));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new SQLException("Invalid stored feature", e); }
        });
    }
    public List<Models.Door> doors() {
        return jdbc.query("SELECT * FROM door ORDER BY id", (rs, row) -> new Models.Door(
            rs.getLong("id"), rs.getString("name"), rs.getString("location"), rs.getBoolean("enabled")));
    }
    public Models.Door door(long id) {
        return doors().stream().filter(d -> d.id() == id).findFirst().orElseThrow(() -> new ApiException(404, "门禁不存在"));
    }
    public long createDoor(Models.DoorInput input) {
        return insert("INSERT INTO door(name,location,enabled) VALUES (?,?,?)", input.name().trim(), input.location().trim(), input.enabled());
    }
    public void updateDoor(long id, Models.DoorInput input) {
        door(id);
        jdbc.update("UPDATE door SET name=?,location=?,enabled=? WHERE id=?", input.name().trim(), input.location().trim(), input.enabled(), id);
    }
    public List<Models.Grant> grants() {
        return jdbc.query("""
            SELECT g.*,p.name AS person_name,p.person_no,d.name AS door_name FROM access_grant g
            JOIN person p ON g.person_id=p.id JOIN door d ON g.door_id=d.id ORDER BY g.id DESC
            """, (rs, row) -> new Models.Grant(rs.getLong("id"), rs.getLong("person_id"), rs.getString("person_name"),
                rs.getString("person_no"), rs.getLong("door_id"), rs.getString("door_name"), time(rs, "valid_from"), time(rs, "valid_until")));
    }
    public boolean authorized(long personId, long doorId, LocalDateTime now) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM access_grant WHERE person_id=? AND door_id=? AND valid_from<=? AND valid_until>=?
            """, Long.class, personId, doorId, now, now) > 0;
    }
    public long saveGrant(Models.GrantInput input) {
        person(input.personId()); door(input.doorId());
        if (!input.validUntil().isAfter(input.validFrom())) throw new ApiException(400, "结束时间必须晚于开始时间");
        var ids = jdbc.queryForList("SELECT id FROM access_grant WHERE person_id=? AND door_id=?", Long.class, input.personId(), input.doorId());
        if (!ids.isEmpty()) {
            jdbc.update("UPDATE access_grant SET valid_from=?,valid_until=? WHERE id=?", input.validFrom(), input.validUntil(), ids.get(0));
            return ids.get(0);
        }
        return insert("INSERT INTO access_grant(person_id,door_id,valid_from,valid_until) VALUES (?,?,?,?)",
            input.personId(), input.doorId(), input.validFrom(), input.validUntil());
    }
    public void deleteGrant(long id) {
        if (jdbc.update("DELETE FROM access_grant WHERE id=?", id) == 0) throw new ApiException(404, "授权不存在");
    }
    public long log(Models.Person person, Models.Door door, boolean allowed, String reason, Double score, LocalDateTime now) {
        return insert("""
            INSERT INTO access_log(person_id,person_name,person_no,door_id,door_name,allowed,reason,score,created_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            """, person == null ? null : person.id(), person == null ? null : person.name(), person == null ? null : person.personNo(),
            door.id(), door.name(), allowed, reason, score, now);
    }
    public Models.Page<Models.Log> logs(String search, Boolean allowed, int page, int size) {
        String term = "%" + search + "%";
        String where = " FROM access_log WHERE (person_name LIKE ? OR person_no LIKE ? OR door_name LIKE ? OR reason LIKE ?)";
        List<Object> args = new ArrayList<>(List.of(term, term, term, term));
        if (allowed != null) { where += " AND allowed=?"; args.add(allowed); }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        args.add(size); args.add((page - 1) * size);
        var items = jdbc.query("SELECT *" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", (rs, row) -> new Models.Log(
            rs.getLong("id"), (Long) rs.getObject("person_id", Long.class), rs.getString("person_name"), rs.getString("person_no"),
            rs.getLong("door_id"), rs.getString("door_name"), rs.getBoolean("allowed"), rs.getString("reason"),
            rs.getObject("score", Double.class), time(rs, "created_at")), args.toArray());
        return new Models.Page<>(items, total, page, size);
    }
    public Map<String, Object> stats(LocalDateTime today) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("persons", jdbc.queryForObject("SELECT COUNT(*) FROM person", Long.class));
        result.put("enrolled", jdbc.queryForObject("SELECT COUNT(*) FROM person WHERE feature IS NOT NULL", Long.class));
        result.put("doors", jdbc.queryForObject("SELECT COUNT(*) FROM door", Long.class));
        result.put("todayAllowed", jdbc.queryForObject("SELECT COUNT(*) FROM access_log WHERE allowed=TRUE AND created_at>=?", Long.class, today));
        result.put("todayDenied", jdbc.queryForObject("SELECT COUNT(*) FROM access_log WHERE allowed=FALSE AND created_at>=?", Long.class, today));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime start = today.minusDays(i), end = start.plusDays(1);
            long allowed = jdbc.queryForObject("SELECT COUNT(*) FROM access_log WHERE allowed=TRUE AND created_at>=? AND created_at<?", Long.class, start, end);
            long denied = jdbc.queryForObject("SELECT COUNT(*) FROM access_log WHERE allowed=FALSE AND created_at>=? AND created_at<?", Long.class, start, end);
            trend.add(Map.of("date", start.toLocalDate().toString(), "allowed", allowed, "denied", denied));
        }
        result.put("trend", trend);
        result.put("recent", logs("", null, 1, 6).items());
        return result;
    }
    private long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[] {"id"});
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
