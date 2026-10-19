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
            long approver;
            try (var connection = DriverManager.getConnection(url, "postgres", "postgres");
                 var sql = connection.createStatement()) {
                try (var row = sql.executeQuery("SELECT id FROM catalog.app_user WHERE username='requester_a'")) {
                    row.next(); owner = row.getLong(1);
                }
                try (var row = sql.executeQuery("SELECT id FROM catalog.app_user WHERE username='approver'")) {
                    row.next(); approver = row.getLong(1);
                }
                sql.execute("INSERT INTO catalog.change_request(requester_id,created_at) VALUES (" + owner + ",now())");
            }
            DatabaseBootstrap.configureLocalAccounts(url);
            String hash = testerHash(url, owner);
            assertThat(new BCryptPasswordEncoder().matches("1", hash)).isTrue();
            assertThat(hash).isNotEqualTo("1");
            DatabaseBootstrap.configureLocalAccounts(url);
            assertThat(testerHash(url, owner)).isEqualTo(hash);
            try (var connection = DriverManager.getConnection(url, "postgres", "postgres"); var sql = connection.createStatement()) {
                try (var row = sql.executeQuery("SELECT username FROM catalog.app_user WHERE active ORDER BY username")) {
                    var names = new java.util.ArrayList<String>();
                    while (row.next()) names.add(row.getString(1));
                    assertThat(names).containsExactly("admin", "tester");
                }
                try (var row = sql.executeQuery("SELECT u.id,u.password_hash,r.role FROM catalog.app_user u JOIN catalog.user_role r ON r.user_id=u.id WHERE username='admin'")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getLong("id")).isEqualTo(approver);
                    assertThat(row.getString("role")).isEqualTo("APPROVER");
                    assertThat(new BCryptPasswordEncoder().matches("1", row.getString("password_hash"))).isTrue();
                }
                try (var row = sql.executeQuery("SELECT count(*) FROM catalog.app_user")) {
                    row.next(); assertThat(row.getInt(1)).isEqualTo(7);
                }
            }
        }
    }

    @Test
    void refusesDuplicateAccountWithoutPartiallyRenamingRequester() throws Exception {
        try (var postgres = EmbeddedPostgres.builder().setLocaleConfig("lc-messages", "C").start()) {
            String url = DatabaseBootstrap.prepare(postgres);
            try (var connection = DriverManager.getConnection(url, "postgres", "postgres"); var sql = connection.createStatement()) {
                sql.execute("INSERT INTO catalog.app_user(username,password_hash,active) SELECT 'admin',password_hash,true FROM catalog.app_user WHERE username='approver'");
                assertThatThrownBy(() -> DatabaseBootstrap.configureLocalAccounts(url))
                        .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("Duplicate local accounts");
                try (var row = sql.executeQuery("SELECT username FROM catalog.app_user WHERE username IN ('requester_a','tester')")) {
                    assertThat(row.next()).isTrue(); assertThat(row.getString(1)).isEqualTo("requester_a");
                }
            }
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
