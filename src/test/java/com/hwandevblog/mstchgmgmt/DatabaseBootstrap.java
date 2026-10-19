package com.hwandevblog.mstchgmgmt;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.*;
import java.util.List;

/**
 * 로컬 시연과 통합 테스트용 DB에 소유자와 앱 계정을 분리해 준비한다.
 * 스키마와 초기 데이터는 소유자 권한으로 생성하고 서버는 제한된 앱 계정으로 연결한다.
 */
public final class DatabaseBootstrap {
    public static final String APP_PASSWORD = "local-app-only";
    public static final String DEMO_PASSWORD = "LocalDemo!2026";
    private DatabaseBootstrap() { }

    public static String prepare(EmbeddedPostgres postgres) throws SQLException {
        try (var connection = postgres.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            sql.execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='mcr_owner') THEN CREATE ROLE mcr_owner NOLOGIN; END IF; END $$");
            sql.execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='mcr_app') THEN CREATE ROLE mcr_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD 'local-app-only'; END IF; END $$");
            try (var result = sql.executeQuery("SELECT 1 FROM pg_database WHERE datname='catalog_review'")) {
                if (!result.next()) {
                    try (var create = connection.createStatement()) { create.execute("CREATE DATABASE catalog_review OWNER mcr_owner"); }
                }
            }
        }
        // URL에 관리자 사용자명이 포함되면 앱 연결 속성을 덮어쓸 수 있어 사용자 없는 URL을 구성한다.
        String url = "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/catalog_review";
        try (var connection = DriverManager.getConnection(url, "postgres", "postgres"); var sql = connection.createStatement()) {
            sql.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
        }
        Flyway.configure().dataSource(url, "postgres", "postgres")
                .initSql("SET ROLE mcr_owner").schemas("catalog").defaultSchema("catalog")
                .locations("classpath:db/migration").load().migrate();
        seed(url);
        return url;
    }

    // 로컬 로그인 계정만 정리하고 과거 요청·처리 이력이 참조하는 사용자 행은 보존한다.
    public static void configureLocalAccounts(String url) throws SQLException {
        try (var connection = DriverManager.getConnection(url, "postgres", "postgres")) {
            connection.setAutoCommit(false);
            long requester = normalizeLocalAccount(connection, "requester_a", "tester");
            long approver = normalizeLocalAccount(connection, "approver", "admin");
            try (var disable = connection.prepareStatement(
                    "UPDATE catalog.app_user SET active=false WHERE id<>? AND id<>? AND active")) {
                disable.setLong(1, requester);
                disable.setLong(2, approver);
                disable.executeUpdate();
            }
            connection.commit();
        }
    }

    private static long normalizeLocalAccount(Connection connection, String previous, String username) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT id,username,password_hash,active FROM catalog.app_user WHERE username IN (?,?) FOR UPDATE")) {
            query.setString(1, previous);
            query.setString(2, username);
            try (var result = query.executeQuery()) {
                if (!result.next()) throw new SQLException("Local account is missing: " + username);
                long id = result.getLong("id");
                String current = result.getString("username");
                String hash = result.getString("password_hash");
                boolean active = result.getBoolean("active");
                if (result.next()) throw new SQLException("Duplicate local accounts; refusing to merge: " + username);
                var encoder = new BCryptPasswordEncoder();
                if (!current.equals(username) || !active || !encoder.matches("1", hash)) {
                    try (var update = connection.prepareStatement(
                            "UPDATE catalog.app_user SET username=?,password_hash=?,active=true WHERE id=?")) {
                        update.setString(1, username);
                        update.setString(2, encoder.encode("1"));
                        update.setLong(3, id);
                        update.executeUpdate();
                    }
                }
                return id;
            }
        }
    }

    private static void seed(String url) throws SQLException {
        try (var connection = DriverManager.getConnection(url, "postgres", "postgres")) {
            connection.setAutoCommit(false);
            try (var sql = connection.createStatement()) {
                sql.execute("SET ROLE mcr_owner");
                sql.execute("SET search_path TO catalog");
                try (var result = sql.executeQuery("SELECT count(*) FROM app_user")) {
                    if (result.next() && result.getLong(1) > 0) { connection.commit(); return; }
                }
            }
            var encoder = new BCryptPasswordEncoder();
            var accounts = List.of("requester_a", "requester_b", "approver", "approver_b", "viewer", "dual", "inactive");
            for (String username : accounts) {
                long id;
                try (var insert = connection.prepareStatement(
                        "INSERT INTO app_user(username,password_hash,active) VALUES(?,?,?) RETURNING id")) {
                    insert.setString(1, username);
                    insert.setString(2, encoder.encode(DEMO_PASSWORD));
                    insert.setBoolean(3, !username.equals("inactive"));
                    try (var result = insert.executeQuery()) { result.next(); id = result.getLong(1); }
                }
                var roles = username.equals("dual") ? List.of("REQUESTER", "APPROVER")
                        : List.of(username.startsWith("approver") ? "APPROVER" : username.equals("viewer") ? "VIEWER" : "REQUESTER");
                for (String role : roles) {
                    try (var insert = connection.prepareStatement("INSERT INTO user_role(user_id,role) VALUES(?,?)")) {
                        insert.setLong(1, id);
                        insert.setString(2, role);
                        insert.executeUpdate();
                    }
                }
            }
            try (var sql = connection.createStatement()) {
                sql.execute("INSERT INTO equipment(code,name,category,daily_fee_krw,active) VALUES "
                        + "('EQ-CAM100','여행용 카메라','CAMERA',15000,true),"
                        + "('EQ-MIC100','스튜디오 마이크','AUDIO',8000,true),"
                        + "('EQ-LGT100','휴대용 조명','LIGHTING',5000,false)");
            }
            connection.commit();
        }
    }
}
