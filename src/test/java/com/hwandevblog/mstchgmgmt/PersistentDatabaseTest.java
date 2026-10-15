package com.hwandevblog.mstchgmgmt;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고정 데이터 디렉터리의 DB를 종료 후 다시 열어 저장값과 초기 계정 보존을 검증한다.
 * 임시 테스트 DB의 자동 정리 설정과 시연 DB의 보존 설정을 구분한다.
 */
class PersistentDatabaseTest {
    @TempDir Path root;

    private EmbeddedPostgres start() throws Exception {
        return EmbeddedPostgres.builder().setDataDirectory(root.resolve("postgres-data"))
                .setCleanDataDirectory(false).setLocaleConfig("lc-messages", "C")
                .setServerConfig("listen_addresses", "127.0.0.1").setServerConfig("fsync", "on").start();
    }

    @Test void restartKeepsRowsAndDoesNotSeedAgain() throws Exception {
        try (var postgres = start()) {
            var url = DatabaseBootstrap.prepare(postgres);
            try (var connection = DriverManager.getConnection(url, "postgres", "postgres"); var sql = connection.createStatement()) {
                sql.executeUpdate("UPDATE catalog.equipment SET daily_fee_krw=17000, version=1 WHERE code='EQ-CAM100'");
                sql.executeUpdate("INSERT INTO catalog.change_request(requester_id,created_at) SELECT id,now() FROM catalog.app_user WHERE username='requester_a'");
                sql.executeUpdate("INSERT INTO catalog.change_revision(request_id,revision_number,state,reason,created_at) SELECT id,1,'DRAFT','재시작 보존 확인',now() FROM catalog.change_request");
                sql.executeUpdate("UPDATE catalog.change_request SET current_revision_id=(SELECT id FROM catalog.change_revision WHERE request_id=change_request.id)");
            }
        }
        try (var postgres = start()) {
            var url = DatabaseBootstrap.prepare(postgres);
            try (var connection = DriverManager.getConnection(url, "mcr_app", DatabaseBootstrap.APP_PASSWORD); var sql = connection.createStatement()) {
                try (var result = sql.executeQuery("SELECT daily_fee_krw, version FROM catalog.equipment WHERE code='EQ-CAM100'")) {
                    result.next();
                    assertThat(result.getLong(1)).isEqualTo(17000);
                    assertThat(result.getLong(2)).isEqualTo(1);
                }
                try (var result = sql.executeQuery("SELECT (SELECT count(*) FROM catalog.app_user), (SELECT count(*) FROM catalog.change_revision), current_setting('fsync')")) {
                    result.next();
                    assertThat(result.getLong(1)).isEqualTo(7);
                    assertThat(result.getLong(2)).isEqualTo(1);
                    assertThat(result.getString(3)).isEqualTo("on");
                }
            }
        }
    }
}
