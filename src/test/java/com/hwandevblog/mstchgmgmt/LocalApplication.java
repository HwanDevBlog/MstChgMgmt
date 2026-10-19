package com.hwandevblog.mstchgmgmt;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * 프로젝트 내부의 고정 데이터 디렉터리로 PostgreSQL과 웹 서버를 실행한다.
 * 중복 실행은 파일 잠금으로 차단하고 종료 시 데이터는 보존한다.
 */
public final class LocalApplication {
    private static EmbeddedPostgres postgres;
    private static FileChannel lockChannel;
    private static FileLock lock;

    public static void main(String[] args) throws Exception {
        var application = webApplication(Path.of("frontend/dist"));
        Path root = Path.of(System.getProperty("mcr.local.root", ".local")).toAbsolutePath().normalize();
        Files.createDirectories(root);
        lockChannel = FileChannel.open(root.resolve("demo.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        lock = lockChannel.tryLock();
        if (lock == null) throw new IllegalStateException("Local database is already running");
        postgres = EmbeddedPostgres.builder().setDataDirectory(root.resolve("postgres-data"))
                .setCleanDataDirectory(false).setPort(15433).setLocaleConfig("lc-messages", "C")
                .setServerConfig("listen_addresses", "127.0.0.1")
                .setServerConfig("fsync", "on").start();
        var url = DatabaseBootstrap.prepare(postgres);
        DatabaseBootstrap.configureLocalTester(url);
        System.setProperty("spring.datasource.url", url);
        System.setProperty("spring.datasource.username", "mcr_app");
        System.setProperty("spring.datasource.password", DatabaseBootstrap.APP_PASSWORD);
        application.run(args);
    }

    // 이클립스 직접 실행은 Gradle 리소스 복사를 거치지 않아 빌드된 화면 경로를 직접 연결한다.
    static SpringApplication webApplication(Path frontend) {
        if (!Files.isRegularFile(frontend.resolve("index.html"))) {
            throw new IllegalStateException("Frontend build missing. Run npm ci and npm run build in frontend first.");
        }
        var application = new SpringApplication(MstChgMgmtApplication.class);
        application.setDefaultProperties(Map.of("spring.web.resources.static-locations",
                frontend.toAbsolutePath().normalize().toUri().toString()));
        return application;
    }
}
