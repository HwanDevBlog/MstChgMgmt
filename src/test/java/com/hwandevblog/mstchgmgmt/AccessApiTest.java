package com.hwandevblog.mstchgmgmt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 DB 계정으로 로그인해 세션, CSRF, 역할과 요청 소유권의 HTTP 적용을 검증한다.
 * 화면 버튼을 거치지 않는 직접 API 호출도 같은 보호 조건을 적용받아야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessApiTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource dataSource;

    @BeforeEach
    void resetDrafts() throws Exception {
        try (var connection = admin(); var sql = connection.createStatement()) {
            sql.executeUpdate("UPDATE change_request SET current_revision_id=NULL");
            sql.executeUpdate("DELETE FROM change_revision");
            sql.executeUpdate("DELETE FROM change_request");
            sql.executeUpdate("UPDATE app_user SET active=(username<>'inactive')");
            sql.executeUpdate("INSERT INTO user_role(user_id,role) SELECT id,'REQUESTER' FROM app_user WHERE username='requester_a' ON CONFLICT DO NOTHING");
        }
    }

    private JsonNode csrf(MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpSession login(String username) throws Exception {
        var session = new MockHttpSession();
        var token = csrf(session);
        var result = mvc.perform(post("/api/auth/login").session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .param("username", username).param("password", DatabaseBootstrap.DEMO_PASSWORD))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private JsonNode create(MockHttpSession session) throws Exception {
        var token = csrf(session);
        return json.readTree(mvc.perform(post("/api/requests").session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType("application/json").content("{\"reason\":\"카탈로그 정비\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test void anonymousCannotReadData() throws Exception {
        mvc.perform(get("/api/equipment")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/requests/mine")).andExpect(status().isUnauthorized());
    }

    @Test void loginAndDraftCreationRequireCsrf() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", "requester_a")
                .param("password", DatabaseBootstrap.DEMO_PASSWORD)).andExpect(status().isForbidden());
        var session = login("requester_a");
        mvc.perform(post("/api/requests").session(session).contentType("application/json")
                .content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/requests").session(session).header("X-CSRF-TOKEN", "forged")
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }

    @Test void passwordAndInactiveAccountAreRejected() throws Exception {
        for (var username : new String[]{"requester_a", "inactive", "missing"}) {
            var session = new MockHttpSession();
            var token = csrf(session);
            mvc.perform(post("/api/auth/login").session(session)
                    .header(token.get("headerName").asText(), token.get("token").asText())
                    .param("username", username).param("password", username.equals("requester_a") ? "wrong" : DatabaseBootstrap.DEMO_PASSWORD))
                    .andExpect(status().isUnauthorized());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"requester_a", "approver", "viewer", "dual"})
    void activeRolesReadEquipmentWithoutPasswordLeak(String username) throws Exception {
        var session = login(username);
        mvc.perform(get("/api/equipment").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].version").value("0"));
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test void ownDraftIsCreatedAndVisibleOnlyToOwner() throws Exception {
        var owner = login("requester_a");
        var created = create(owner);
        long id = created.get("requestId").asLong();
        assertThat(created.get("state").asText()).isEqualTo("DRAFT");
        mvc.perform(get("/api/requests/" + id).session(owner)).andExpect(status().isOk());
        mvc.perform(get("/api/requests/mine").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value(id));
        for (var other : new String[]{"requester_b", "approver", "viewer"}) {
            mvc.perform(get("/api/requests/" + id).session(login(other))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/requests/mine").session(login("requester_b")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest @ValueSource(strings = {"approver", "viewer"})
    void readOnlyRolesCannotCreateOrReadMine(String username) throws Exception {
        var session = login(username);
        var token = csrf(session);
        mvc.perform(post("/api/requests").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/mine").session(session)).andExpect(status().isForbidden());
    }

    @Test void clientCannotChooseRequesterId() throws Exception {
        var session = login("requester_a");
        var token = csrf(session);
        mvc.perform(post("/api/requests").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType("application/json").content("{\"reason\":\"test\",\"requesterId\":2}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/requests/mine").session(session)).andExpect(content().json("[]"));
        var draft = create(session);
        var me = json.readTree(mvc.perform(get("/api/auth/me").session(session)).andReturn().getResponse().getContentAsString());
        assertThat(draft.get("requesterId")).isEqualTo(me.get("id"));
    }

    @Test void draftReasonLengthIsCheckedWithoutCreatingPartialRows() throws Exception {
        var session = login("requester_a");
        var token = csrf(session);
        mvc.perform(post("/api/requests").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("reason", "가".repeat(501)))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/requests/mine").session(session)).andExpect(content().json("[]"));
    }

    @Test void inactiveSessionIsInvalidatedOnNextRequest() throws Exception {
        var session = login("requester_a");
        try (var connection = admin(); var sql = connection.createStatement()) {
            sql.executeUpdate("UPDATE app_user SET active=false WHERE username='requester_a'");
        }
        mvc.perform(get("/api/equipment").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void revokedRoleDoesNotSurviveInSession() throws Exception {
        var session = login("requester_a");
        try (var connection = admin(); var sql = connection.createStatement()) {
            sql.executeUpdate("DELETE FROM user_role WHERE user_id=(SELECT id FROM app_user WHERE username='requester_a')");
        }
        mvc.perform(get("/api/requests/mine").session(session)).andExpect(status().isForbidden());
    }

    @Test void logoutRequiresCsrfAndEndsAccess() throws Exception {
        var session = login("requester_a");
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        var token = csrf(session);
        mvc.perform(post("/api/auth/logout").session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())).andExpect(status().isNoContent());
        mvc.perform(get("/api/equipment")).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void loginRotatesSessionIdAndRejectsPreviousCsrfToken() throws Exception {
        var session = new MockHttpSession();
        String previousId = session.getId();
        var previousToken = csrf(session);
        mvc.perform(post("/api/auth/login").session(session)
                .header(previousToken.get("headerName").asText(), previousToken.get("token").asText())
                .param("username", "requester_a").param("password", DatabaseBootstrap.DEMO_PASSWORD))
                .andExpect(status().isNoContent());
        assertThat(session.getId()).isNotEqualTo(previousId);
        mvc.perform(post("/api/requests").session(session)
                .header(previousToken.get("headerName").asText(), previousToken.get("token").asText())
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        create(session);
    }

    @Test void submittedAndAppliedReadPermissionsFollowPolicy() throws Exception {
        var draft = create(login("requester_a"));
        long id = draft.get("requestId").asLong();
        // 조회 권한만 검증하기 위한 상태 fixture이며 실제 제출·승인 처리는 수행하지 않는다.
        try (var connection = admin(); var sql = connection.createStatement()) {
            sql.executeUpdate("UPDATE change_revision SET state='SUBMITTED'");
        }
        mvc.perform(get("/api/requests/" + id).session(login("approver"))).andExpect(status().isOk());
        mvc.perform(get("/api/requests/" + id).session(login("viewer"))).andExpect(status().isForbidden());
        try (var connection = admin(); var sql = connection.createStatement()) {
            sql.executeUpdate("UPDATE change_revision SET state='APPLIED'");
        }
        mvc.perform(get("/api/requests/" + id).session(login("viewer"))).andExpect(status().isOk());
    }

    @Test void revisionIdFromAnotherRequestCannotBeSubstituted() throws Exception {
        var owner = login("requester_a");
        var first = create(owner);
        var second = create(owner);
        mvc.perform(get("/api/requests/" + first.get("requestId").asLong()
                + "/revisions/" + second.get("revisionId").asLong()).session(owner))
                .andExpect(status().isNotFound());
    }

    @Test void databaseConnectionCannotActAsSchemaOwner() throws Exception {
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            try (var result = sql.executeQuery("SELECT current_user, rolcreatedb, rolsuper FROM pg_roles WHERE rolname=current_user")) {
                result.next();
                assertThat(result.getString(1)).isEqualTo("mcr_app");
                assertThat(result.getBoolean(2)).isFalse();
                assertThat(result.getBoolean(3)).isFalse();
            }
            assertThatThrownBy(() -> sql.execute("UPDATE catalog.app_user SET active=false"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> sql.execute("CREATE TABLE catalog.forbidden_table(id bigint)"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> sql.execute("SET ROLE mcr_owner"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test void currentRevisionMustBelongToSameRequest() throws Exception {
        var owner = login("requester_a");
        var first = create(owner);
        var second = create(owner);
        try (var connection = admin(); var sql = connection.prepareStatement("UPDATE change_request SET current_revision_id=? WHERE id=?")) {
            sql.setLong(1, second.get("revisionId").asLong());
            sql.setLong(2, first.get("requestId").asLong());
            assertThatThrownBy(sql::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }
    }
}
