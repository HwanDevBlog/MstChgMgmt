package com.hwandevblog.mstchgmgmt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwandevblog.mstchgmgmt.request.SubmissionService;
import com.hwandevblog.mstchgmgmt.security.SessionUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 PostgreSQL에서 검토 식별자 대조와 불변 제출본·상태의 원자적 저장을 검증한다.
 * 제출 이후 파일 교체, 잘못된 재요청과 직접 DB 변경도 통제되어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubmissionApiTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SubmissionService service;
    @Autowired com.hwandevblog.mstchgmgmt.account.AppUserRepository users;
    private MockHttpSession owner;
    private String base;
    private long requestId,revisionId;
    private final String original="operation,equipment_code,name,category,daily_fee_krw,active\n"
            +"UPDATE,EQ-CAM100,여행용 카메라,CAMERA,17000,true\n";

    @BeforeEach void prepare() throws Exception {
        try(var connection=admin();var sql=connection.createStatement()) {
            sql.execute("TRUNCATE revision_submission");
            sql.execute("UPDATE change_request SET current_revision_id=NULL");
            sql.execute("DELETE FROM change_revision");sql.execute("DELETE FROM change_request");
            sql.execute("UPDATE equipment SET daily_fee_krw=15000,version=0 WHERE code='EQ-CAM100'");
            sql.execute("UPDATE app_user SET active=(username<>'inactive')");
            sql.execute("INSERT INTO user_role(user_id,role) SELECT id,'REQUESTER' FROM app_user WHERE username='requester_a' ON CONFLICT DO NOTHING");
        }
        owner=login("requester_a");
        var draft=write(owner,"/api/requests","{\"reason\":\"대여료 조정\"}",201);
        requestId=draft.path("requestId").asLong();revisionId=draft.path("revisionId").asLong();
        base="/api/requests/"+requestId+"/revisions/"+revisionId;
        upload(original);validate();
    }
    @AfterEach void cleanup() throws Exception {
        try(var connection=admin();var sql=connection.createStatement()) {
            sql.execute("DROP TRIGGER IF EXISTS fail_submission_state ON change_revision");
            sql.execute("DROP FUNCTION IF EXISTS fail_submission_state()");
            sql.execute("TRUNCATE revision_submission");
            sql.execute("UPDATE equipment SET daily_fee_krw=15000,version=0 WHERE code='EQ-CAM100'");
        }
    }
    private JsonNode token(MockHttpSession session)throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString());
    }
    private MockHttpSession login(String username)throws Exception {
        var session=new MockHttpSession();var csrf=token(session);
        return (MockHttpSession)mvc.perform(post("/api/auth/login").session(session).header(csrf.path("headerName").asText(),csrf.path("token").asText())
                .param("username",username).param("password",DatabaseBootstrap.DEMO_PASSWORD)).andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    private JsonNode write(MockHttpSession session,String path,String body,int expected)throws Exception {
        var csrf=token(session);
        var response=mvc.perform(post(path).session(session).header(csrf.path("headerName").asText(),csrf.path("token").asText())
                .contentType("application/json").content(body)).andExpect(status().is(expected)).andReturn().getResponse();
        return response.getContentAsString().isBlank()?json.createObjectNode():json.readTree(response.getContentAsString());
    }
    private JsonNode read(String path)throws Exception {
        return json.readTree(mvc.perform(get(path).session(owner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode upload(String contents)throws Exception {
        var csrf=token(owner);
        return json.readTree(mvc.perform(multipart(base+"/file").file(new MockMultipartFile("file","catalog.csv","text/csv",contents.getBytes(StandardCharsets.UTF_8)))
                .session(owner).header(csrf.path("headerName").asText(),csrf.path("token").asText())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private void validate()throws Exception {write(owner,base+"/validation","{\"fileId\":\""+read(base+"/workspace").path("fileId").asText()+"\"}",200);}
    private String input(JsonNode proposal)throws Exception {
        var node=json.createObjectNode();for(var name:new String[]{"fileId","validationId","contentHash"})node.set(name,proposal.get(name));return json.writeValueAsString(node);
    }

    @Test void freezesDisplayedBundleAndOriginalAndReturnsSameReceiptOnRetry()throws Exception {
        var proposed=read(base+"/submission-preview");var body=input(proposed);
        var submitted=write(owner,base+"/submission",body,200);
        assertThat(submitted.path("contentHash")).isEqualTo(proposed.path("contentHash"));
        assertThat(submitted.path("rows")).isEqualTo(proposed.path("rows"));
        assertThat(submitted.path("submissionId").asText()).isNotBlank();
        assertThat(read(base+"/submission")).isEqualTo(submitted);
        assertThat(write(owner,base+"/submission",body,200)).isEqualTo(submitted);
        mvc.perform(get("/api/requests/"+requestId).session(owner)).andExpect(jsonPath("$.state").value("SUBMITTED"));
        try(var connection=admin();var sql=connection.createStatement()) {
            sql.execute("UPDATE revision_workspace SET original=decode('626164','hex'),filename='tampered.csv'");
            try(var rs=sql.executeQuery("SELECT (SELECT count(*) FROM revision_submission),daily_fee_krw,version FROM equipment WHERE code='EQ-CAM100'")) {
                rs.next();assertThat(rs.getInt(1)).isEqualTo(1);assertThat(rs.getLong(2)).isEqualTo(15000);assertThat(rs.getLong(3)).isZero();
            }
        }
        mvc.perform(get(base+"/file").session(owner)).andExpect(content().bytes(original.getBytes(StandardCharsets.UTF_8)));
        write(owner,base+"/reason","{\"reason\":\"변경 시도\"}",409);
    }

    @Test void rejectsOldValidationFileAndReasonBundleInsteadOfSilentlyRefreshing()throws Exception {
        var proposed=read(base+"/submission-preview");
        validate();
        assertThat(write(owner,base+"/submission",input(proposed),409).path("code").asText()).isEqualTo("SUBMISSION_MISMATCH");
        proposed=read(base+"/submission-preview");
        write(owner,base+"/reason","{\"reason\":\"사유 변경\"}",200);
        write(owner,base+"/submission",input(proposed),409);
        proposed=read(base+"/submission-preview");upload(original);
        write(owner,base+"/submission",input(proposed),409);
    }

    @Test void rejectsCatalogConflictAndMissingReasonAndUnchangedOnlyFile()throws Exception {
        var proposed=read(base+"/submission-preview");
        try(var connection=admin();var sql=connection.createStatement()){sql.execute("UPDATE equipment SET version=1 WHERE code='EQ-CAM100'");}
        assertThat(write(owner,base+"/submission",input(proposed),409).path("code").asText()).isEqualTo("STALE_CATALOG");
        write(owner,base+"/reason","{\"reason\":\"\"}",200);
        mvc.perform(get(base+"/submission-preview").session(owner)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        write(owner,base+"/reason","{\"reason\":\"정비\"}",200);
        upload(original.replace("17000","15000"));validate();
        mvc.perform(get(base+"/submission-preview").session(owner)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_CHANGES"));
    }

    @ParameterizedTest @ValueSource(strings={"requester_b","approver","viewer"})
    void otherActorsCannotPrepareOrSubmitOwnersDraft(String actor)throws Exception {
        var proposed=read(base+"/submission-preview");var other=login(actor);
        mvc.perform(get(base+"/submission-preview").session(other)).andExpect(status().isForbidden());
        write(other,base+"/submission",input(proposed),403);
        write(other,base+"/reason","{\"reason\":\"조작\"}",403);
    }

    @Test void submittedSnapshotIsReadableByOwnerAndApproverButNotViewerOrAnotherRequester()throws Exception {
        write(owner,base+"/submission",input(read(base+"/submission-preview")),200);
        mvc.perform(get(base+"/submission").session(login("approver"))).andExpect(status().isOk());
        mvc.perform(get(base+"/submission").session(login("viewer"))).andExpect(status().isForbidden());
        mvc.perform(get(base+"/submission").session(login("requester_b"))).andExpect(status().isForbidden());
    }

    @Test void databaseRejectsSnapshotMutationAndApplicationTruncate()throws Exception {
        write(owner,base+"/submission",input(read(base+"/submission-preview")),200);
        String url;try(var connection=admin()){url=connection.getMetaData().getURL();}
        for(var statement:new String[]{"UPDATE catalog.revision_submission SET filename='changed'","DELETE FROM catalog.revision_submission","TRUNCATE catalog.revision_submission"}) {
            try(var connection=DriverManager.getConnection(url,"mcr_app",DatabaseBootstrap.APP_PASSWORD);var sql=connection.createStatement()) {
                assertThatThrownBy(()->sql.execute(statement)).isInstanceOf(java.sql.SQLException.class).satisfies(error->assertThat(((java.sql.SQLException)error).getSQLState()).isEqualTo("42501"));
            }
        }
        for(var statement:new String[]{"UPDATE revision_submission SET filename='changed'","DELETE FROM revision_submission"}) {
            try(var connection=admin();var sql=connection.createStatement()) {
                assertThatThrownBy(()->sql.execute(statement)).isInstanceOf(java.sql.SQLException.class).satisfies(error->assertThat(((java.sql.SQLException)error).getSQLState()).isEqualTo("55000"));
            }
        }
    }

    @Test void rollsBackInsertedSnapshotWhenStateChangeFails()throws Exception {
        try(var connection=admin();var sql=connection.createStatement()) {
            sql.execute("CREATE FUNCTION fail_submission_state() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'state failure'; END $$");
            sql.execute("CREATE TRIGGER fail_submission_state BEFORE UPDATE ON change_revision FOR EACH ROW EXECUTE FUNCTION fail_submission_state()");
        }
        var proposed=read(base+"/submission-preview");
        var user=SessionUser.from(users.findByUsername("requester_a").orElseThrow());
        assertThatThrownBy(()->service.submit(user,requestId,revisionId,new SubmissionService.SubmitInput(UUID.fromString(proposed.path("fileId").asText()),
                UUID.fromString(proposed.path("validationId").asText()),proposed.path("contentHash").asText()))).isInstanceOf(RuntimeException.class);
        try(var connection=admin();var sql=connection.createStatement();var rs=sql.executeQuery("SELECT (SELECT count(*) FROM revision_submission),state FROM change_revision WHERE id="+revisionId)) {
            rs.next();assertThat(rs.getInt(1)).isZero();assertThat(rs.getString(2)).isEqualTo("DRAFT");
        }
    }

    @Test void concurrentIdenticalSubmissionsCreateOneReceipt()throws Exception {
        var proposed=read(base+"/submission-preview");var user=SessionUser.from(users.findByUsername("requester_a").orElseThrow());
        var input=new SubmissionService.SubmitInput(UUID.fromString(proposed.path("fileId").asText()),UUID.fromString(proposed.path("validationId").asText()),proposed.path("contentHash").asText());
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<SubmissionService.Snapshot> action=()->{start.await();return service.submit(user,requestId,revisionId,input);};
            var first=executor.submit(action);var second=executor.submit(action);start.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS)).isEqualTo(second.get(10,TimeUnit.SECONDS));
        }
    }

    @Test void rejectsAReviewedRevisionAfterTheCurrentPointerChanges()throws Exception {
        var proposed=read(base+"/submission-preview");
        try(var connection=admin();var sql=connection.createStatement()) {
            sql.execute("INSERT INTO change_revision(request_id,revision_number,state,reason,created_at) VALUES("+requestId+",2,'DRAFT','새 검토',now())");
            sql.execute("UPDATE change_request SET current_revision_id=(SELECT id FROM change_revision WHERE request_id="+requestId+" AND revision_number=2) WHERE id="+requestId);
        }
        assertThat(write(owner,base+"/submission",input(proposed),409).path("code").asText()).isEqualTo("STALE_REVISION");
        mvc.perform(get(base+"/submission-preview").session(owner)).andExpect(status().isConflict());
    }

    @Test void requiresCsrfAndRejectsClientSuppliedChangeValues()throws Exception {
        var body=input(read(base+"/submission-preview"));
        mvc.perform(post(base+"/submission").session(owner).contentType("application/json").content(body)).andExpect(status().isForbidden());
        write(owner,base+"/submission",body.substring(0,body.length()-1)+",\"rows\":[]}",400);
    }
}
