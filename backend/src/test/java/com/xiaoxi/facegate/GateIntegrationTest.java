package com.xiaoxi.facegate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Collections;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring MVC, validation, SQL and business decisions; Redis/Python are isolated doubles. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GateIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GateRepository repo;
    @Autowired GateService service;
    @MockitoBean RedisState redis;
    @MockitoBean FaceClient face;
    long personId, doorId;
    private final List<Double> feature = Collections.nCopies(128, 0.1);

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM access_log"); jdbc.update("DELETE FROM access_grant");
        jdbc.update("DELETE FROM person"); jdbc.update("DELETE FROM door");
        personId = repo.createPerson(new Models.PersonInput("S001", "测试人员", "计算机学院", true));
        doorId = repo.createDoor(new Models.DoorInput("实验室", "A301", true));
        repo.enroll(personId, feature, service.now());
        when(redis.session("test-token")).thenReturn("admin");
        when(redis.allowLogin(anyString())).thenReturn(true);
        when(redis.createSession("admin")).thenReturn("new-token");
        when(redis.acquireDoor(anyLong(), anyLong())).thenReturn(true);
        when(face.extract(any())).thenReturn(new Models.Feature(feature, "sface-2021dec", List.of(0, 0, 100, 100)));
        when(face.match(anyList(), anyList(), anyDouble(), anyDouble())).thenReturn(new Models.Match(personId, 0.91, 0.1, false));
    }
    private MockMultipartFile photo() { return new MockMultipartFile("file", "face.jpg", "image/jpeg", new byte[] {1, 2, 3}); }
    private void grant() {
        repo.saveGrant(new Models.GrantInput(personId, doorId, service.now().minusDays(1), service.now().plusDays(1)));
    }
    private org.springframework.test.web.servlet.ResultActions scan() throws Exception {
        return mvc.perform(multipart("/api/access/recognize").file(photo()).param("doorId", String.valueOf(doorId)).header("Authorization", "Bearer test-token"));
    }
    @Test void protectedRoutesRequireLogin() throws Exception {
        mvc.perform(get("/api/persons")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/persons").header("Authorization", "Bearer expired")).andExpect(status().isUnauthorized());
    }
    @Test void loginChecksRealPasswordHash() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("""
            {"username":"admin","password":"TestPassword_2026!"}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.token").value("new-token"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content("""
            {"username":"admin","password":"wrong"}
            """)).andExpect(status().isUnauthorized());
    }
    @Test void loginThrottleRejectsBeforePasswordCheck() throws Exception {
        when(redis.allowLogin(anyString())).thenReturn(false);
        mvc.perform(post("/api/auth/login").contentType("application/json").content("""
            {"username":"admin","password":"wrong"}
            """)).andExpect(status().isTooManyRequests());
    }
    @Test void authorizedIdentityOpensDoorAndWritesLog() throws Exception {
        grant();
        scan().andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true))
            .andExpect(jsonPath("$.doorOpen").value(true)).andExpect(jsonPath("$.reason").value("ALLOWED"));
        assertThat(repo.logs("", true, 1, 10).total()).isEqualTo(1);
    }
    @Test void absentPermissionDenies() throws Exception {
        scan().andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("NO_PERMISSION"));
        verify(redis, never()).acquireDoor(anyLong(), anyLong());
    }
    @Test void expiredPermissionDenies() throws Exception {
        repo.saveGrant(new Models.GrantInput(personId, doorId, service.now().minusDays(2), service.now().minusDays(1)));
        scan().andExpect(jsonPath("$.allowed").value(false)).andExpect(jsonPath("$.reason").value("NO_PERMISSION"));
    }
    @Test void futurePermissionDenies() throws Exception {
        repo.saveGrant(new Models.GrantInput(personId, doorId, service.now().plusDays(1), service.now().plusDays(2)));
        scan().andExpect(jsonPath("$.reason").value("NO_PERMISSION"));
    }
    @Test void disabledPersonDenies() throws Exception {
        grant(); repo.updatePerson(personId, new Models.PersonInput("S001", "测试人员", "计算机学院", false));
        scan().andExpect(jsonPath("$.reason").value("PERSON_DISABLED"));
    }
    @Test void disabledDoorDeniesWithoutCallingPython() throws Exception {
        repo.updateDoor(doorId, new Models.DoorInput("实验室", "A301", false));
        scan().andExpect(jsonPath("$.reason").value("DOOR_DISABLED"));
        verify(face, never()).extract(any());
    }
    @Test void unknownAndAmbiguousFacesDeny() throws Exception {
        when(face.match(anyList(), anyList(), anyDouble(), anyDouble())).thenReturn(new Models.Match(null, 0.2, 0.1, false));
        scan().andExpect(jsonPath("$.reason").value("UNKNOWN_FACE"));
        when(face.match(anyList(), anyList(), anyDouble(), anyDouble())).thenReturn(new Models.Match(null, 0.7, 0.69, true));
        scan().andExpect(jsonPath("$.reason").value("AMBIGUOUS_FACE"));
    }
    @Test void cooldownDeniesRepeatedOpening() throws Exception {
        grant(); when(redis.acquireDoor(personId, doorId)).thenReturn(false);
        scan().andExpect(jsonPath("$.doorOpen").value(false)).andExpect(jsonPath("$.reason").value("COOLDOWN"));
    }
    @Test void invalidImageAndServiceOutageFailClosedAndAreLogged() throws Exception {
        doThrow(new ApiException(422, "未检测到人脸")).when(face).extract(any());
        scan().andExpect(jsonPath("$.reason").value("INVALID_IMAGE"));
        doThrow(new ApiException(503, "服务不可用")).when(face).extract(any());
        scan().andExpect(jsonPath("$.reason").value("FACE_UNAVAILABLE"));
        assertThat(repo.logs("", false, 1, 10).total()).isEqualTo(2);
    }
    @Test void cannotEnrollAnIdentityAlreadyAssignedToSomeoneElse() throws Exception {
        long second = repo.createPerson(new Models.PersonInput("S002", "另一人", "", true));
        mvc.perform(multipart("/api/persons/{id}/face", second).file(photo()).header("Authorization", "Bearer test-token"))
            .andExpect(status().isConflict());
        assertThat(repo.person(second).enrolled()).isFalse();
    }
    @Test void deletedPersonRetainsLogSnapshotAndRemovesPermission() throws Exception {
        grant(); scan(); repo.deletePerson(personId);
        var logs = repo.logs("测试人员", null, 1, 10);
        assertThat(logs.total()).isEqualTo(1);
        assertThat(logs.items().get(0).personId()).isNull();
        assertThat(logs.items().get(0).personName()).isEqualTo("测试人员");
        assertThat(repo.grants()).isEmpty();
    }
    @Test void validatesPayloadsAndPagination() throws Exception {
        mvc.perform(post("/api/persons").header("Authorization", "Bearer test-token").contentType("application/json")
            .content("{\"personNo\":\"\",\"name\":\"\",\"department\":\"\",\"enabled\":true}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/logs?page=0").header("Authorization", "Bearer test-token")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/persons?size=101").header("Authorization", "Bearer test-token")).andExpect(status().isBadRequest());
    }
    @Test void grantUpsertAndDashboardUseDatabaseData() throws Exception {
        grant(); grant(); assertThat(repo.grants()).hasSize(1);
        when(face.healthy()).thenReturn(true);
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer test-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.persons").value(1))
            .andExpect(jsonPath("$.enrolled").value(1)).andExpect(jsonPath("$.trend.length()").value(7));
    }
}
