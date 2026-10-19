package com.hwandevblog.mstchgmgmt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * 이클립스 로컬 실행기가 클래스패스 밖의 화면과 정적 파일을 제공하는지 확인한다.
 * 임시 화면·테스트 DB를 사용해 시연 파일과 데이터를 변경하지 않는다.
 */
class LocalFrontendTest extends PostgresIntegrationTest {
    @TempDir Path frontend;

    @Test
    void servesExternalFrontendAndKeepsApiAuthentication() throws Exception {
        Files.createDirectories(frontend.resolve("assets"));
        Files.writeString(frontend.resolve("index.html"), "<!doctype html><html><body>eclipse-frontend-check</body></html>");
        Files.writeString(frontend.resolve("assets/check.css"), "body { color: green; }");
        Files.writeString(frontend.resolve("assets/check.js"), "console.log('frontend-check');");
        String url;
        try (var connection = admin()) { url = connection.getMetaData().getURL(); }
        try (var context = (ServletWebServerApplicationContext) LocalApplication.webApplication(frontend).run(
                "--server.port=0", "--spring.datasource.url=" + url,
                "--spring.datasource.username=mcr_app", "--spring.datasource.password=" + DatabaseBootstrap.APP_PASSWORD)) {
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + context.getWebServer().getPort();
            for (String path : new String[]{"/", "/index.html", "/assets/check.css", "/assets/check.js"}) {
                var response = client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(path).isEqualTo(200);
                if (path.equals("/")) assertThat(response.body()).contains("eclipse-frontend-check");
            }
            var unauthenticated = client.send(HttpRequest.newBuilder(URI.create(base + "/api/equipment")).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertThat(unauthenticated.statusCode()).isEqualTo(401);
        }
    }

    @Test
    void explainsMissingFrontendBeforeStartingDatabase() {
        assertThatThrownBy(() -> LocalApplication.webApplication(frontend))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("npm run build");
    }
}
