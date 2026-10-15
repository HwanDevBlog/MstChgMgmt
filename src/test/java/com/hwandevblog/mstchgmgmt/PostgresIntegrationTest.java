package com.hwandevblog.mstchgmgmt;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * 실제 PostgreSQL에서 마이그레이션과 제한된 앱 연결을 사용하는 테스트 기반을 제공한다.
 * 관리자 연결은 테스트 데이터 준비에만 사용한다.
 */
public abstract class PostgresIntegrationTest {
    private static final EmbeddedPostgres POSTGRES;
    private static final String URL;
    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().setLocaleConfig("lc-messages", "C")
                    .setServerConfig("listen_addresses", "127.0.0.1").start();
            URL = DatabaseBootstrap.prepare(POSTGRES);
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> "mcr_app");
        registry.add("spring.datasource.password", () -> DatabaseBootstrap.APP_PASSWORD);
    }

    protected Connection admin() throws Exception {
        var connection = DriverManager.getConnection(URL, "postgres", "postgres");
        try (var sql = connection.createStatement()) { sql.execute("SET search_path TO catalog"); }
        return connection;
    }
}
