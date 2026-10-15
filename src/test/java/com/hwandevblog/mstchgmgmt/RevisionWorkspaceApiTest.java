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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 PostgreSQL과 제한된 앱 연결에서 업로드·검증·원본 접근을 검증한다.
 * 파일 교체와 기준정보 변경 후에도 이전 검증을 현재 결과로 사용할 수 없어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RevisionWorkspaceApiTest extends PostgresIntegrationTest {
    private static final String HEADER = "operation,equipment_code,name,category,daily_fee_krw,active\n";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private MockHttpSession owner;
    private String base;

    @BeforeEach
    void prepare() throws Exception {
        try (var connection = admin(); var sql = connection.createStatement()) {
            sql.execute("UPDATE change_request SET current_revision_id=NULL");
            sql.execute("DELETE FROM change_revision");
            sql.execute("DELETE FROM change_request");
            sql.execute("DELETE FROM equipment WHERE code NOT IN ('EQ-CAM100','EQ-MIC100','EQ-LGT100')");
            sql.execute("UPDATE equipment SET daily_fee_krw=15000,version=0 WHERE code='EQ-CAM100'");
            sql.execute("UPDATE app_user SET active=(username<>'inactive')");
            sql.execute("INSERT INTO user_role(user_id,role) SELECT id,'REQUESTER' FROM app_user WHERE username='requester_a' ON CONFLICT DO NOTHING");
        }
        owner = login("requester_a");
        var draft = create(owner);
        base = "/api/requests/" + draft.path("requestId").asLong() + "/revisions/" + draft.path("revisionId").asLong();
    }

    @org.junit.jupiter.api.AfterEach
    void restoreEquipment() throws Exception {
        try (var connection = admin(); var sql = connection.createStatement()) {
            sql.execute("DELETE FROM equipment WHERE code NOT IN ('EQ-CAM100','EQ-MIC100','EQ-LGT100')");
            sql.execute("UPDATE equipment SET daily_fee_krw=15000,version=0 WHERE code='EQ-CAM100'");
        }
    }

    private JsonNode token(MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString());
    }
    private MockHttpSession login(String name) throws Exception {
        var session = new MockHttpSession();
        var csrf = token(session);
        return (MockHttpSession) mvc.perform(post("/api/auth/login").session(session)
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .param("username", name).param("password", DatabaseBootstrap.DEMO_PASSWORD))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    private JsonNode create(MockHttpSession session) throws Exception {
        var csrf = token(session);
        return json.readTree(mvc.perform(post("/api/requests").session(session)
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .contentType("application/json").content("{\"reason\":\"대여료 변경 검토\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode upload(String content) throws Exception {
        var csrf = token(owner);
        return json.readTree(mvc.perform(multipart(base + "/file")
                .file(new MockMultipartFile("file", "catalog.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8)))
                .session(owner).header(csrf.path("headerName").asText(), csrf.path("token").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode validate(JsonNode file) throws Exception {
        var csrf = token(owner);
        return json.readTree(mvc.perform(post(base + "/validation").session(owner)
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .contentType("application/json").content("{\"fileId\":\"" + file.path("fileId").asText() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void preservesOriginalAndClassifiesNewChangedAndUnchangedWithExactNumbers() throws Exception {
        String csv = "\uFEFF" + HEADER + "UPDATE,EQ-CAM100,여행용 카메라,CAMERA,17000,true\n"
                + "CREATE,EQ-TRI200,삼각대,ACCESSORY,9223372036854775807,true\n"
                + "UPDATE,EQ-LGT100,휴대용 조명,LIGHTING,5000,false\n";
        var file = upload(csv);
        assertThat(file.path("preview").isNull()).isTrue();
        assertThat(file.path("originalSha256").asText()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(csv.getBytes(StandardCharsets.UTF_8))));
        var preview = validate(file);
        assertThat(preview.path("valid").asBoolean()).isTrue();
        assertThat(preview.path("created").asInt()).isEqualTo(1);
        assertThat(preview.path("changed").asInt()).isEqualTo(1);
        assertThat(preview.path("unchanged").asInt()).isEqualTo(1);
        assertThat(preview.at("/rows/0/before/dailyFeeKrw").asText()).isEqualTo("15000");
        assertThat(preview.at("/rows/0/expectedVersion").asText()).isEqualTo("0");
        assertThat(preview.at("/rows/1/after/dailyFeeKrw").asText()).isEqualTo("9223372036854775807");
        mvc.perform(get(base + "/file").session(owner)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(csv.getBytes(StandardCharsets.UTF_8)));
        mvc.perform(get(base + "/workspace").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.preview.validationId").value(preview.path("validationId").asText()));
        // 검증만으로 실제 장비가 바뀌거나 신규 장비가 생성되어서는 안 된다.
        try (var connection = admin(); var sql = connection.createStatement();
             var rs = sql.executeQuery("SELECT daily_fee_krw,version,(SELECT count(*) FROM equipment WHERE code='EQ-TRI200') FROM equipment WHERE code='EQ-CAM100'")) {
            rs.next(); assertThat(rs.getLong(1)).isEqualTo(15000); assertThat(rs.getLong(2)).isZero(); assertThat(rs.getInt(3)).isZero();
        }
    }

    @Test void reportsDatabaseExistenceAndFieldErrorsWithoutSuccessfulValidation() throws Exception {
        var preview = validate(upload(HEADER + "CREATE,EQ-CAM100,카메라,CAMERA,1,true\n"
                + "UPDATE,EQ-XXX999,없는 장비,AUDIO,1,true\nCREATE,EQ-NEW001,장비,AUDIO,-1,true\n"));
        assertThat(preview.path("valid").asBoolean()).isFalse();
        assertThat(preview.path("errors").toString()).contains("CREATE_EXISTS", "UPDATE_MISSING", "INVALID_FEE");
        assertThat(preview.path("rows").size()).isZero();
    }

    @Test void replacingFileInvalidatesPreviewAndRejectsPreviousFileIdentity() throws Exception {
        var old = upload(HEADER + "CREATE,EQ-NEW001,신규,AUDIO,10,true\n");
        validate(old);
        var replacement = upload("bad header\n");
        assertThat(replacement.path("fileId").asText()).isNotEqualTo(old.path("fileId").asText());
        mvc.perform(get(base + "/workspace").session(owner)).andExpect(jsonPath("$.preview").isEmpty());
        var csrf = token(owner);
        mvc.perform(post(base + "/validation").session(owner).header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .contentType("application/json").content("{\"fileId\":\"" + old.path("fileId").asText() + "\"}"))
                .andExpect(status().isConflict());
        assertThat(validate(replacement).path("errors").toString()).contains("HEADER_MISMATCH");
    }

    @Test void catalogChangeMarksStoredPreviewStaleUntilRevalidation() throws Exception {
        var file = upload(HEADER + "UPDATE,EQ-CAM100,여행용 카메라,CAMERA,17000,true\n");
        var old = validate(file);
        try (var connection = admin(); var sql = connection.createStatement()) {
            sql.execute("UPDATE equipment SET daily_fee_krw=16000,version=1 WHERE code='EQ-CAM100'");
        }
        mvc.perform(get(base + "/workspace").session(owner)).andExpect(jsonPath("$.preview.stale").value(true))
                .andExpect(jsonPath("$.preview.rows[0].before.dailyFeeKrw").value("15000"));
        var updated = validate(file);
        assertThat(updated.path("validationId").asText()).isNotEqualTo(old.path("validationId").asText());
        assertThat(updated.at("/rows/0/expectedVersion").asText()).isEqualTo("1");
        assertThat(updated.at("/rows/0/before/dailyFeeKrw").asText()).isEqualTo("16000");
    }

    @Test void newEquipmentAppearingAfterPreviewIsDetected() throws Exception {
        var file = upload(HEADER + "CREATE,EQ-NEW001,신규,AUDIO,10,true\n");
        validate(file);
        try (var connection = admin(); var sql = connection.createStatement()) {
            sql.execute("INSERT INTO equipment VALUES('EQ-NEW001','다른 신규','AUDIO',20,true,0)");
        }
        mvc.perform(get(base + "/workspace").session(owner)).andExpect(jsonPath("$.preview.stale").value(true));
        assertThat(validate(file).path("errors").toString()).contains("CREATE_EXISTS");
    }

    @ParameterizedTest @ValueSource(strings = {"requester_b", "approver", "viewer"})
    void otherActorsCannotReadOrReplaceOrValidateDraftFiles(String name) throws Exception {
        var file = upload(HEADER + "CREATE,EQ-NEW001,신규,AUDIO,10,true\n");
        var other = login(name);
        mvc.perform(get(base + "/workspace").session(other)).andExpect(status().isForbidden());
        mvc.perform(get(base + "/file").session(other)).andExpect(status().isForbidden());
        var csrf = token(other);
        mvc.perform(multipart(base + "/file").file(new MockMultipartFile("file", "evil.csv", "text/csv", new byte[0]))
                .session(other).header(csrf.path("headerName").asText(), csrf.path("token").asText())).andExpect(status().isForbidden());
        mvc.perform(post(base + "/validation").session(other).header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .contentType("application/json").content("{\"fileId\":\"" + file.path("fileId").asText() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void requiresCsrfAndEnforcesUploadByteLimitWithoutReplacingFile() throws Exception {
        var old = upload(HEADER + "CREATE,EQ-NEW001,신규,AUDIO,10,true\n");
        mvc.perform(multipart(base + "/file").file(new MockMultipartFile("file", new byte[0])).session(owner))
                .andExpect(status().isForbidden());
        var csrf = token(owner);
        mvc.perform(multipart(base + "/file").file(new MockMultipartFile("file", new byte[1048577])).session(owner)
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())).andExpect(status().isPayloadTooLarge());
        mvc.perform(get(base + "/workspace").session(owner)).andExpect(jsonPath("$.fileId").value(old.path("fileId").asText()));
    }

    @Test void previousRevisionIsReadableButNotEditableAndWrongRequestPairIsRejected() throws Exception {
        var file = upload(HEADER + "CREATE,EQ-NEW001,신규,AUDIO,10,true\n");
        try (var connection = admin(); var sql = connection.createStatement()) {
            sql.execute("INSERT INTO change_revision(request_id,revision_number,state,reason,created_at) SELECT id,2,'DRAFT','다음 리비전',now() FROM change_request");
            sql.execute("UPDATE change_request SET current_revision_id=(SELECT id FROM change_revision WHERE request_id=change_request.id AND revision_number=2)");
        }
        mvc.perform(get(base + "/file").session(owner)).andExpect(status().isOk());
        var csrf = token(owner);
        mvc.perform(post(base + "/validation").session(owner).header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .contentType("application/json").content("{\"fileId\":\"" + file.path("fileId").asText() + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_REVISION"));
        var another = create(owner);
        String wrong = "/api/requests/" + another.path("requestId").asLong() + "/revisions/" + base.split("/")[5];
        mvc.perform(get(wrong + "/workspace").session(owner)).andExpect(status().isNotFound());
    }

    @Test void nonDraftStateRejectsUploadAndValidation() throws Exception {
        var file = upload(HEADER + "CREATE,EQ-NEW001,신규,AUDIO,10,true\n");
        try (var connection = admin(); var sql = connection.createStatement()) {
            sql.execute("UPDATE change_revision SET state='SUBMITTED'");
        }
        var csrf = token(owner);
        mvc.perform(multipart(base + "/file").file(new MockMultipartFile("file", new byte[0])).session(owner)
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())).andExpect(status().isConflict());
        mvc.perform(post(base + "/validation").session(owner).header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .contentType("application/json").content("{\"fileId\":\"" + file.path("fileId").asText() + "\"}"))
                .andExpect(status().isConflict());
    }
}
