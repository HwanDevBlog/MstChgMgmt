package com.hwandevblog.mstchgmgmt;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.*;

/**
 * 로컬 시연 계정 변경이 소유권·역할과 암호 해시를 보존하는지 별도 DB에서 검증한다.
 */
class LocalDemoAccountTest {
    @Test
    void renamesRequesterWithoutChangingOwnershipAndIsIdempotent() throws Exception {
        try (var postgres = EmbeddedPostgres.builder().setLocaleConfig("lc-messages", "C").start()) {
            String url = DatabaseBootstrap.prepare(postgres);
            long owner;
            try (var connection = DriverManager.getConnection(url, "postgres", "postgres");
                 var sql = connection.createStatement()) {
                try (var row = sql.executeQuery("SELECT id FROM catalog.app_user WHERE username='requester_a'")) {
                    row.next(); owner = row.getLong(1);
                }
                sql.execute("INSERT INTO catalog.change_request(requester_id,created_at) VALUES (" + owner + ",now())");
            }
            DatabaseBootstrap.configureLocalTester(url);
            String hash = testerHash(url, owner);
            assertThat(new BCryptPasswordEncoder().matches("1", hash)).isTrue();
            assertThat(hash).isNotEqualTo("1");
            DatabaseBootstrap.configureLocalTester(url);
            assertThat(testerHash(url, owner)).isEqualTo(hash);
        }
    }

    private String testerHash(String url, long owner) throws Exception {
        try (var connection = DriverManager.getConnection(url, "postgres", "postgres");
             var sql = connection.createStatement();
             var row = sql.executeQuery("SELECT u.id,u.password_hash,r.requester_id,ur.role FROM catalog.app_user u "
                     + "JOIN catalog.change_request r ON r.requester_id=u.id "
                     + "JOIN catalog.user_role ur ON ur.user_id=u.id WHERE u.username='tester'")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getLong("id")).isEqualTo(owner);
            assertThat(row.getLong("requester_id")).isEqualTo(owner);
            assertThat(row.getString("role")).isEqualTo("REQUESTER");
            return row.getString("password_hash");
        }
    }
}
