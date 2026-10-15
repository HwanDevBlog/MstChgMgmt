package com.hwandevblog.mstchgmgmt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwandevblog.mstchgmgmt.domain.RevisionPolicy;
import com.hwandevblog.mstchgmgmt.request.DecisionService;
import com.hwandevblog.mstchgmgmt.security.SessionUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 PostgreSQL에서 승인 반영과 성공 이력의 원자성·권한·경합을 검증한다.
 * 테스트 데이터와 실패 주입은 시연 DB가 아닌 별도의 임시 DB에만 적용한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DecisionApiTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DecisionService service;
    @Autowired com.hwandevblog.mstchgmgmt.account.AppUserRepository users;
    private static final String HEADER="operation,equipment_code,name,category,daily_fee_krw,active\n";
    private static final String CAMERA="UPDATE,EQ-CAM100,여행용 카메라,CAMERA,17000,true\n";
    private MockHttpSession owner,approver;
    private JsonNode snapshot;
    private String base;

    @BeforeEach void prepare()throws Exception {
        cleanup();owner=login("requester_a");approver=login("approver");
        snapshot=submit(owner,HEADER+CAMERA+"CREATE,EQ-NEW001,새 장비,ACCESSORY,3000,true\nUPDATE,EQ-LGT100,휴대용 조명,LIGHTING,5000,false\n");
        base="/api/requests/"+snapshot.path("requestId").asLong();
    }
    @AfterEach void cleanup()throws Exception {
        try(var connection=admin();var sql=connection.createStatement()) {
            sql.execute("DROP TRIGGER IF EXISTS fail_decision_state ON change_revision");sql.execute("DROP FUNCTION IF EXISTS fail_decision_state()");
            sql.execute("DROP TRIGGER IF EXISTS fail_equipment_update ON equipment");
            sql.execute("DROP TRIGGER IF EXISTS block_create_race ON equipment");
            sql.execute("DROP FUNCTION IF EXISTS fail_equipment_update()");sql.execute("DROP FUNCTION IF EXISTS block_create_race()");
            sql.execute("TRUNCATE equipment_change,revision_decision,revision_submission");
            sql.execute("UPDATE change_request SET current_revision_id=NULL");sql.execute("DELETE FROM change_revision");sql.execute("DELETE FROM change_request");
            sql.execute("DELETE FROM equipment");
            sql.execute("INSERT INTO equipment VALUES('EQ-CAM100','여행용 카메라','CAMERA',15000,true,0),('EQ-MIC100','스튜디오 마이크','AUDIO',8000,true,0),('EQ-LGT100','휴대용 조명','LIGHTING',5000,false,0)");
            sql.execute("UPDATE app_user SET active=(username<>'inactive')");
            sql.execute("INSERT INTO user_role(user_id,role) SELECT id,'REQUESTER' FROM app_user WHERE username='requester_a' ON CONFLICT DO NOTHING");
            sql.execute("INSERT INTO user_role(user_id,role) SELECT id,'APPROVER' FROM app_user WHERE username='approver' ON CONFLICT DO NOTHING");
        }
    }
    private JsonNode token(MockHttpSession session)throws Exception {return json.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString());}
    private MockHttpSession login(String username)throws Exception {
        var session=new MockHttpSession();var token=token(session);
        return (MockHttpSession)mvc.perform(post("/api/auth/login").session(session).header(token.path("headerName").asText(),token.path("token").asText())
                .param("username",username).param("password",DatabaseBootstrap.DEMO_PASSWORD)).andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    private JsonNode write(MockHttpSession session,String path,String body,int expected)throws Exception {
        var token=token(session);var result=mvc.perform(post(path).session(session).header(token.path("headerName").asText(),token.path("token").asText())
                .contentType("application/json").content(body)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return result.isBlank()?json.createObjectNode():json.readTree(result);
    }
    private JsonNode read(MockHttpSession session,String path)throws Exception {return json.readTree(mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    private JsonNode submit(MockHttpSession session,String csv)throws Exception {
        var draft=write(session,"/api/requests","{\"reason\":\"장비 조정\"}",201);
        String revision="/api/requests/"+draft.path("requestId").asLong()+"/revisions/"+draft.path("revisionId").asLong();
        var token=token(session);
        var uploaded=json.readTree(mvc.perform(multipart(revision+"/file").file(new MockMultipartFile("file","catalog.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8)))
                .session(session).header(token.path("headerName").asText(),token.path("token").asText())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        write(session,revision+"/validation","{\"fileId\":\""+uploaded.path("fileId").asText()+"\"}",200);
        var proposed=read(session,revision+"/submission-preview");
        var input=json.createObjectNode();for(var key:new String[]{"fileId","validationId","contentHash"})input.set(key,proposed.get(key));
        return write(session,revision+"/submission",input.toString(),200);
    }
    private String input(JsonNode bundle,String reason) {
        var body=json.createObjectNode().put("revisionId",bundle.path("revisionId").asLong()).put("contentHash",bundle.path("contentHash").asText());
        if(reason!=null)body.put("reason",reason);return body.toString();
    }
    private String revisionPath(JsonNode bundle){return "/api/requests/"+bundle.path("requestId").asLong()+"/revisions/"+bundle.path("revisionId").asLong();}
    private long count(String table)throws Exception {
        try(var connection=admin();var sql=connection.createStatement();var rs=sql.executeQuery("SELECT count(*) FROM "+table)){rs.next();return rs.getLong(1);}
    }
    private void equipmentUntouched()throws Exception {
        try(var connection=admin();var sql=connection.createStatement();var rs=sql.executeQuery("SELECT daily_fee_krw,version FROM equipment WHERE code='EQ-CAM100'")) {
            rs.next();assertThat(rs.getLong(1)).isEqualTo(15000);assertThat(rs.getLong(2)).isZero();
        }
        assertThat(count("equipment_change")).isZero();
    }
    private void unchangedCatalog()throws Exception {
        equipmentUntouched();assertThat(count("revision_decision")).isZero();
    }
    private DecisionService.Input command(JsonNode bundle){return new DecisionService.Input(bundle.path("revisionId").asLong(),bundle.path("contentHash").asText(),null);}
    private SessionUser user(String name){return SessionUser.from(users.findByUsername(name).orElseThrow());}

    @Test void appliesOnlyActualChangesAndKeepsUnchangedVersionAndHistoryStableOnRetry()throws Exception {
        var receipt=write(approver,base+"/approve",input(snapshot,null),200);
        assertThat(receipt.path("state").asText()).isEqualTo("APPLIED");assertThat(receipt.path("changes").size()).isEqualTo(2);
        assertThat(write(approver,base+"/approve",input(snapshot,null),200)).isEqualTo(receipt);
        assertThat(count("revision_decision")).isEqualTo(1);assertThat(count("equipment_change")).isEqualTo(2);
        try(var connection=admin();var sql=connection.createStatement();var rs=sql.executeQuery("SELECT code,daily_fee_krw,version FROM equipment WHERE code IN ('EQ-CAM100','EQ-LGT100','EQ-NEW001') ORDER BY code")) {
            rs.next();assertThat(rs.getLong(2)).isEqualTo(17000);assertThat(rs.getLong(3)).isEqualTo(1);
            rs.next();assertThat(rs.getLong(3)).isZero();rs.next();assertThat(rs.getLong(3)).isZero();
        }
        var viewer=login("viewer");assertThat(read(viewer,"/api/history/applied").size()).isEqualTo(1);
        assertThat(read(viewer,revisionPath(snapshot)+"/decision")).isEqualTo(receipt);
        mvc.perform(get(revisionPath(snapshot)+"/file").session(viewer)).andExpect(status().isForbidden());
        write(owner,base+"/revisions",input(snapshot,null),409);
    }

    @Test void rejectsSelfDecisionEvenForDualRoleAndFiltersOwnRequestFromPending()throws Exception {
        var dual=login("dual");var own=submit(dual,HEADER+CAMERA);
        String path="/api/requests/"+own.path("requestId").asLong();
        write(dual,path+"/approve",input(own,null),403);write(dual,path+"/reject",input(own,"반려"),403);
        var pending=read(dual,"/api/approvals/pending");assertThat(pending.toString()).doesNotContain("\"requestId\":"+own.path("requestId").asLong()+",");
        write(owner,base+"/approve",input(snapshot,null),403);write(login("viewer"),base+"/approve",input(snapshot,null),403);
        unchangedCatalog();
    }

    @Test void rejectsUnchangedRowVersionConflictBeforeAnyWritesAndKeepsSubmittedState()throws Exception {
        try(var connection=admin();var sql=connection.createStatement()){sql.execute("UPDATE equipment SET version=1 WHERE code='EQ-LGT100'");}
        var failure=write(approver,base+"/approve",input(snapshot,null),409);
        assertThat(failure.path("code").asText()).isEqualTo("CATALOG_CONFLICT");assertThat(failure.path("traceId").asText()).isNotBlank();
        unchangedCatalog();assertThat(read(owner,base).path("state").asText()).isEqualTo("SUBMITTED");
    }

    @Test void rejectsCreateCollisionAndIncorrectRenderedHash()throws Exception {
        var bad=json.readTree(input(snapshot,null));((com.fasterxml.jackson.databind.node.ObjectNode)bad).put("contentHash","0".repeat(64));
        write(approver,base+"/approve",bad.toString(),409);
        try(var connection=admin();var sql=connection.createStatement()){sql.execute("INSERT INTO equipment VALUES('EQ-NEW001','다른 신규','AUDIO',1,true,0)");}
        assertThat(write(approver,base+"/approve",input(snapshot,null),409).path("code").asText()).isEqualTo("CREATE_EXISTS");unchangedCatalog();
    }

    @Test void rollsBackFirstEquipmentUpdateAndHistoryWhenLaterUpdateFails()throws Exception {
        var bundle=submit(owner,HEADER+CAMERA+"UPDATE,EQ-MIC100,스튜디오 마이크,AUDIO,9000,true\n");
        try(var connection=admin();var sql=connection.createStatement()) {
            sql.execute("CREATE FUNCTION fail_equipment_update() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.code='EQ-MIC100' THEN RAISE EXCEPTION 'injected later row failure'; END IF; RETURN NEW; END $$");
            sql.execute("CREATE TRIGGER fail_equipment_update BEFORE UPDATE ON equipment FOR EACH ROW EXECUTE FUNCTION fail_equipment_update()");
        }
        var failure=write(approver,"/api/requests/"+bundle.path("requestId").asLong()+"/approve",input(bundle,null),500);
        assertThat(failure.path("code").asText()).isEqualTo("TRANSACTION_FAILED");assertThat(failure.path("traceId").asText()).isNotBlank();
        unchangedCatalog();assertThat(read(owner,"/api/requests/"+bundle.path("requestId").asLong()).path("state").asText()).isEqualTo("SUBMITTED");
    }

    @Test void rollsBackEquipmentDecisionAndHistoryWhenFinalStateUpdateFails()throws Exception {
        try(var connection=admin();var sql=connection.createStatement()) {
            sql.execute("CREATE FUNCTION fail_decision_state() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.state='APPLIED' THEN RAISE EXCEPTION 'injected final state failure'; END IF; RETURN NEW; END $$");
            sql.execute("CREATE TRIGGER fail_decision_state BEFORE UPDATE ON change_revision FOR EACH ROW EXECUTE FUNCTION fail_decision_state()");
        }
        var failure=write(approver,base+"/approve",input(snapshot,null),500);
        assertThat(failure.path("code").asText()).isEqualTo("TRANSACTION_FAILED");
        unchangedCatalog();assertThat(read(owner,base).path("state").asText()).isEqualTo("SUBMITTED");
        try(var connection=admin();var sql=connection.createStatement();var rs=sql.executeQuery("SELECT count(*) FROM equipment WHERE code='EQ-NEW001'")) {
            rs.next();assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test void rejectsWithReasonAndCreatesNewDraftWithoutChangingOldSubmission()throws Exception {
        write(approver,base+"/reject",input(snapshot,""),400);
        write(approver,base+"/reject",input(snapshot,"대여료 재검토"),200);equipmentUntouched();
        var frozen=read(owner,revisionPath(snapshot)+"/submission");assertThat(frozen).isEqualTo(snapshot);
        var next=write(owner,base+"/revisions",input(snapshot,null),200);
        assertThat(next.path("revisionNumber").asInt()).isEqualTo(2);assertThat(next.path("state").asText()).isEqualTo("DRAFT");
        assertThat(write(owner,base+"/revisions",input(snapshot,null),200).path("revisionId")).isEqualTo(next.path("revisionId"));
        String nextPath=base+"/revisions/"+next.path("revisionId").asLong();
        var workspace=read(owner,nextPath+"/workspace");assertThat(workspace.path("preview").isNull()).isTrue();assertThat(workspace.path("fileId")).isNotEqualTo(snapshot.path("fileId"));
        assertThat(read(owner,base+"/history").size()).isEqualTo(2);
        write(approver,base+"/approve",input(snapshot,null),409);
        assertThat(read(owner,revisionPath(snapshot)).path("state").asText()).isEqualTo("REJECTED");
    }

    @Test void withdrawIsOwnerOnlyAndPreservesOriginalForANewRevision()throws Exception {
        write(login("requester_b"),base+"/withdraw",input(snapshot,"철회"),403);
        write(owner,base+"/withdraw",input(snapshot,"변경안 조정"),200);
        var next=write(owner,base+"/revisions",input(snapshot,null),200);
        mvc.perform(get(base+"/revisions/"+next.path("revisionId").asLong()+"/file").session(owner)).andExpect(status().isOk());
        assertThat(read(owner,revisionPath(snapshot)).path("state").asText()).isEqualTo("WITHDRAWN");
        assertThat(count("equipment_change")).isZero();
    }

    @Test void sameApproverConcurrentRetryReturnsOneImmutableReceipt()throws Exception {
        var actor=user("approver");var command=command(snapshot);var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<DecisionService.Receipt> task=()->{start.await();return service.decide(actor,snapshot.path("requestId").asLong(),command,RevisionPolicy.Action.APPROVE);};
            var a=executor.submit(task);var b=executor.submit(task);start.countDown();assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
        }
        assertThat(count("revision_decision")).isEqualTo(1);assertThat(count("equipment_change")).isEqualTo(2);
    }

    @Test void differentApproversCannotBothClaimTheSameDecision()throws Exception {
        var start=new CountDownLatch(1);var a=user("approver");var b=user("approver_b");
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->attempt(start,a,snapshot));var second=executor.submit(()->attempt(start,b,snapshot));start.countDown();
            var outcomes=List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(value->value instanceof DecisionService.Receipt).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(value->value instanceof org.springframework.web.server.ResponseStatusException).count()).isEqualTo(1);
        }
        assertThat(count("revision_decision")).isEqualTo(1);
    }

    private Object attempt(CountDownLatch start,SessionUser actor,JsonNode bundle)throws Exception {
        start.await();try{return service.decide(actor,bundle.path("requestId").asLong(),command(bundle),RevisionPolicy.Action.APPROVE);}
        catch(RuntimeException error){return error;}
    }
    @Test void uniqueConstraintResolvesConcurrentCreationAcrossDifferentRequests()throws Exception {
        var first=submit(owner,HEADER+"CREATE,EQ-RACE01,동시 신규,AUDIO,100,true\n");
        var second=submit(owner,HEADER+"CREATE,EQ-RACE01,동시 신규,AUDIO,100,true\n");
        try(var gate=admin();var sql=gate.createStatement()) {
            sql.execute("CREATE FUNCTION block_create_race() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.code='EQ-RACE01' THEN PERFORM pg_advisory_xact_lock(4242); END IF; RETURN NEW; END $$");
            sql.execute("CREATE TRIGGER block_create_race BEFORE INSERT ON equipment FOR EACH ROW EXECUTE FUNCTION block_create_race()");
            sql.execute("SELECT pg_advisory_lock(4242)");
            var start=new CountDownLatch(1);var actor=user("approver");
            try(var executor=Executors.newFixedThreadPool(2)) {
                var a=executor.submit(()->attempt(start,actor,first));var b=executor.submit(()->attempt(start,actor,second));start.countDown();
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);boolean bothWaiting=false;
                try {
                    while(System.nanoTime()<deadline) {
                        try(var result=sql.executeQuery("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND objid=4242 AND NOT granted")){result.next();if(result.getInt(1)==2){bothWaiting=true;break;}}
                        Thread.sleep(20);
                    }
                } finally {sql.execute("SELECT pg_advisory_unlock(4242)");}
                assertThat(bothWaiting).as("both transactions passed the missing-row check before INSERT").isTrue();
                var outcomes=List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
                assertThat(outcomes.stream().filter(value->value instanceof DecisionService.Receipt).count()).isEqualTo(1);
                assertThat(outcomes.stream().filter(value->value instanceof org.springframework.dao.DuplicateKeyException).count()).isEqualTo(1);
            }
        }
        assertThat(count("revision_decision")).isEqualTo(1);assertThat(count("equipment_change")).isEqualTo(1);
        assertThat(List.of(read(owner,"/api/requests/"+first.path("requestId").asLong()).path("state").asText(),
                read(owner,"/api/requests/"+second.path("requestId").asLong()).path("state").asText())).containsExactlyInAnyOrder("APPLIED","SUBMITTED");
    }

    @Test void retryStillChecksRoleAndImmutableAuditPermissions()throws Exception {
        write(approver,base+"/approve",input(snapshot,null),200);
        try(var connection=admin();var sql=connection.createStatement()) {sql.execute("DELETE FROM user_role WHERE role='APPROVER' AND user_id=(SELECT id FROM app_user WHERE username='approver')");}
        write(approver,base+"/approve",input(snapshot,null),403);
        try(var connection=admin();var sql=connection.createStatement()) {
            assertThatThrownBy(()->sql.execute("UPDATE revision_decision SET reason='tamper'")).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(()->sql.execute("DELETE FROM equipment_change")).isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test void requiresCsrfAndRejectsClientSuppliedEquipmentValues()throws Exception {
        mvc.perform(post(base+"/approve").session(approver).contentType("application/json").content(input(snapshot,null))).andExpect(status().isForbidden());
        String body=input(snapshot,null);write(approver,base+"/approve",body.substring(0,body.length()-1)+",\"rows\":[]}",400);
        unchangedCatalog();
    }
}
