# 작업 인계 — 로컬 요청자·승인자 두 계정 정리 완료 (2026-10-19)

## 확정된 작업 경로·상태

- 프로젝트: `C:\toyProject\workspace\MstChgMgmt`
- 로컬 Git 브랜치: `main`. 최근 커밋 `09b8a34`(이클립스 실행 오류 수정 및 테스트 계정과 화면 UI 개선)의 push·두 CI 성공을 앞선 턴에서 확인했다. 이후 로컬 tester/admin 두 계정 정리 9개 파일은 미커밋이다.
- 1단계 완료: 독립 CSV 규칙, 권한표, 상태 전이, 구현과 단위 테스트.
- 2단계 완료: 세션 로그인·CSRF, PostgreSQL/Flyway, 조회·본인 임시 요청 생성과 API 소유권 통제, React 화면. 전체 MVP는 미완료.
- 3단계 완료: CSV 업로드·원본 보존·DB 존재 검증·전후 비교·기준정보 변경 감지.
- 4단계 완료: 임시 사유 수정·내용 해시·불변 제출본·검토 묶음 대조·SUBMITTED 제출. 이후 5단계에서 승인·반영·반려·철회·새 리비전을 연결했다.
- 5단계 완료: 승인 원자 반영·실제 변경 이력·동시 결정·멱등 재요청·반려·철회·새 리비전과 역할별 화면.
- 6단계 완료: CI 설정·시연 안내, 실제 GitHub Backend CI·Frontend CI 성공 확인.
- 기존 참고 자료는 읽기 전용으로 확인했으며 프로젝트로 복사하지 않았다.
- 회사 저장소·DB는 조회하지 않았다. 전역 Git 설정은 바꾸지 않았다.

## 원본 확인과 환경

지정된 경력기술서의 접근 제어·인증 이력·전체 롤백·삭제 전 증빙·개인정보 출력 통제 구간,
검증근거 3-8절의 해당 근거, 포트폴리오 14쪽의 담당 전환을 원본에서 확인했다.
기존 경험이 없었다는 표현은 사용하지 않는다. 관련 회사 구현·파일·업무 규칙을 새 프로젝트에 가져오지 않는다.
초기 DESIGN.md는 과거 참고 문서로만 읽었고, 과거 설치 상태나 지시를 이번 지시로 적용하지 않았다.

InvMgmt는 로컬 `C:\toyProject\workspace\InvMgmt`에서 확인했다.
HEAD가 `2ca8fdd33b00ef36a074559d4e3866af97873aa2`와 일치하고 변경 목록은 없었다.
Stock @Version, StockRepository·PurchaseOrderRepository의 PESSIMISTIC_WRITE,
Spring Boot 3.5.16, Gradle 9.1.0, Embedded PostgreSQL 2.2.2 + 바이너리 BOM 16.14.0을 확인했다.
원격 페이지 직접 조회는 실패했지만 로컬 커밋 원본 확인으로 보완했다.
기존 LocalApplication은 임시 데이터 디렉터리이므로 시연 데이터 보존 구성으로 재사용하지 않는다.
기존 프로젝트의 표준 Gradle wrapper 실행 파일·JAR만 빌드 기반으로 사용했다. 업무 소스는 새로 작성했다.

사용 가능한 도구:

- Java 21.0.11 JDK: `C:\toyProject\eclipse\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_21.0.11.v20260515-1531\jre`
- Node 24.20.0 / npm 11.19.0: `C:\toyProject\tools\node`
- Codex 번들 Node 24.19.0도 PATH에 있으므로 프론트 단계에서는 사용할 실행 파일을 명시할 것.
- PATH에서 java·javac·npm·psql·mvn·gradle은 발견되지 않았다. PostgreSQL 서비스도 발견되지 않았다.
  이는 PC 전체에 모든 PostgreSQL 바이너리가 없다는 증명은 아니다.
- 새 경로는 사전 존재하지 않았고, 생성·실제 파일 작성으로 쓰기 가능함을 확인했다.

## 1단계 주요 파일

| 절대 경로 | 내용 |
|---|---|
| `C:\toyProject\workspace\MstChgMgmt\docs\DESIGN.md` | CSV catalog-v1, 권한표, 전이표, 후속 제출본·DB 계약 |
| `C:\toyProject\workspace\MstChgMgmt\src\main\java\com\hwandevblog\mstchgmgmt\domain\CatalogRules.java` | 상한·카테고리·사유 규칙 |
| `C:\toyProject\workspace\MstChgMgmt\src\main\java\com\hwandevblog\mstchgmgmt\domain\CatalogCsvValidator.java` | CSV 검증·레코드/필드 오류·원본 SHA-256 |
| `C:\toyProject\workspace\MstChgMgmt\src\main\java\com\hwandevblog\mstchgmgmt\domain\RevisionPolicy.java` | 활성·역할·소유권·현재 리비전·상태 정책 |
| `C:\toyProject\workspace\MstChgMgmt\src\test\java\com\hwandevblog\mstchgmgmt\domain\CatalogCsvValidatorTest.java` | 34건 |
| `C:\toyProject\workspace\MstChgMgmt\src\test\java\com\hwandevblog\mstchgmgmt\domain\RevisionPolicyTest.java` | 22건 |
| `C:\toyProject\workspace\MstChgMgmt\scripts\dev.ps1` | Java 탐색·프로세스 환경·프로젝트 Gradle 캐시·Windows 우회 |
| `C:\toyProject\workspace\MstChgMgmt\samples\catalog-create.csv` | 독립 예제 |

## 1단계 검증 결과

```powershell
cd C:\toyProject\workspace\MstChgMgmt
.\scripts\dev.ps1 -Tasks test,bootJar
```

최종 결과: **56건 통과, 실패 0, 오류 0, 건너뜀 0**, bootJar 성공.
생성 JAR `C:\toyProject\workspace\MstChgMgmt\build\libs\MstChgMgmt-0.1.0-SNAPSHOT.jar`의
Spring Boot 시작과 정상 종료도 확인했다. 웹 서버 의존성이 없는 1단계 진입점이다.
보고서: `C:\toyProject\workspace\MstChgMgmt\build\reports\tests\test\index.html`.

테스트 민감도 실험:

| 임시 변경 | 실행 테스트 | 결과 |
|---|---|---|
| approver 계산에서 `!owner &&` 제거 | `*RevisionPolicyTest.dualRoleCannotDecideOwnRequest` | 예상대로 2건 실패 |
| 현재 리비전 비교 조건 제거 | `*RevisionPolicyTest.authorizedActorsMustStillMatchCurrentRevision` | 예상대로 1건 실패 |

두 실험 후 finally로 소스를 복원했고 파일 SHA-256이 원본과 같음을 확인했다.
이후 최종 전체 단위 테스트를 다시 통과했다.
실험 로그는 커밋하지 않는 `C:\toyProject\workspace\MstChgMgmt\.local\verification`에 있다.
CSV는 BOM·CRLF·따옴표·중복 양쪽 오류·UTF-8 오류·200/201레코드·정확한 바이트 경계·정수 오버플로·부분 파싱 폐기를 검증했다.
정책은 비활성 계정, 타인 ID 접근·철회, 이중 역할 자기 결정, 과거 리비전, 전이표를 검증했다.

## 빌드 환경 문제와 해결

Gradle 8.14.3 첫 실행에서 변환 캐시의 디렉터리 이동 AccessDeniedException이 재현됐다.
프로젝트 캐시로 옮겨도 동일했으며, 계측 에이전트를 끈 실행에서 빌드가 통과했다.
최종 wrapper는 공식 Gradle 8.14.6과 SHA-256을 고정했다. Spring Boot 3.5의 공식 Gradle 8 지원 범위다.
Windows dev.ps1과 프로젝트별 Eclipse Buildship 설정에서 `-Dorg.gradle.internal.instrumentation.agent=false`를 전달한다.
OS 보안 프로그램이나 글로벌 설정은 변경하지 않았다.
원인을 보안 프로그램 하나로 확정하지 않는다. 확인된 사실은 해당 옵션 적용 시 이 프로젝트 빌드가 통과했다는 점이다.

## 스키마 변경·미해결 항목

V1 Flyway 마이그레이션으로 계정·역할·장비·요청·리비전 테이블, 현재 리비전 복합 FK, 인덱스, 앱 권한을 생성했다.
CSV 업로드·원본 보존·DB 존재 검증·전후 비교·제출본 고정·철회·반려·승인·반영 이력은 미구현이다.
반영 동시성·전체 롤백·멱등 재시도·화면 제출 묶음 일치·GitHub Actions는 후속 검증이다.
현재 상태 정책의 자기 승인·구 리비전 보호를 승인 API 구현 완료로 표현하면 안 된다.

## 다음 작업

**admin 로그인·요청 #30 승인 시연과 계정 변경 커밋 확인**을 같은 채팅에서 이어간다.
+
+1. 현재 서버에는 tester/1·admin/1만 로그인 가능하도록 반영했다. 사용자가 요청 #30을 admin으로 승인했고 APPLIED·실제 기준정보·반영 이력을 확인했다. 현재 보기 스크롤 수정도 반영되어 Ctrl+F5 후 확인한다.
+2. 계정 정리와 보기 스크롤 수정 11개 파일을 사용자 직접 커밋한다. 기존 gradlew.bat 줄바꿈 변경은 제외한다. 통합 커밋 메시지는 로컬 계정 정리 및 상세 보기 스크롤 수정.
+3. 커밋 완료 통보 시 로컬 HEAD와 GitHub main, 해당 커밋의 두 CI 결과를 확인한다. 사용자 요청 없이 커밋·push하지 않는다.
+
## 작업 경로 이동 — 2026-10-15

사용자 요청으로 프로젝트 전체를 `C:\toyProject\workspace\MstChgMgmt`로 이동했다.
기존 경로는 제거되었고 Git 저장소·소스·검증 로그·캐시를 함께 보존했다.
이동 직후 주요 소스·설정·문서 20개 파일의 SHA-256 일치를 확인했다.
README와 이 문서의 실행·인계 경로를 갱신했다. 실행 스크립트는 프로젝트 위치를 자동 계산하므로 수정이 필요 없었다.
새 위치에서 `scripts\dev.ps1 -Tasks test,--offline` 실행 결과 56건 통과, 실패·오류 0건이다.


## 축약명 적용 — 2026-10-15

기존 InvMgmt와 같은 축약 명명 방식을 적용해 프로젝트명을 `MstChgMgmt`(Master Change Management)로 확정했다.
현재 폴더는 `C:\toyProject\workspace\MstChgMgmt`, Gradle rootProject.name도 `MstChgMgmt`다.
문서의 경로와 JAR 이름을 갱신했다. Java 패키지와 업무 코드는 변경하지 않았다.
`scripts\dev.ps1 -Tasks bootJar,--offline` 성공 및 `build\libs\MstChgMgmt-0.1.0-SNAPSHOT.jar` 생성을 확인했다.
이름 변경 검증에서는 단위 테스트를 반복 실행하지 않았으며, 마지막 단위 테스트 결과는 앞서 확인한 56건 통과다.

## Eclipse Gradle 가져오기 오류 — 2026-10-15

Eclipse의 `The supplied phased action failed with an exception` 요약을 workspace `.metadata/.log`와 대조했다.
실제 원인은 공유 Gradle 8.14.6 transforms 캐시 이동 시 AccessDeniedException이었다.
스크립트의 우회 설정이 Eclipse에는 적용되지 않은 것을 확인했다.
`C:\toyProject\workspace\MstChgMgmt\.settings\org.eclipse.buildship.core.prefs`의 프로젝트별 override를 켜고,
Gradle Arguments·JVM Arguments에 계측 에이전트 비활성화 옵션을 지정했다.
Gradle user home은 프로젝트 `.gradle-user-home`, Java home은 기존 Eclipse Java 21.0.11 번들로 설정했다.
기존 prefs 백업은 `.local/verification/buildship-prefs-before-fix.txt`에 보관했다.

해당 prefs를 Java Properties로 읽어 동일한 설정으로 Gradle Tooling API의 EclipseProject 모델을 조회했다.
결과: CONFIGURE SUCCESSFUL, 프로젝트 MstChgMgmt, 소스 디렉터리 2개, 클래스패스 의존성 53개.
진단 코드는 커밋하지 않는 `.local/verification/VerifyEclipseModel.java`에 있다.
업무 코드와 스키마는 변경하지 않았으므로 단위 테스트를 반복 실행하지 않았다.
Eclipse 화면의 오류 마커 해소는 프로젝트 F5 → Gradle → Refresh Gradle Project 후 확인이 필요하다.
README에 Eclipse 재현 설정을 추가했으며 PC별 `.settings`와 진단 파일은 Git 제외 상태를 확인했다.
## InvMgmt와 개발 방식 통일 — 2026-10-15

사용자 요청으로 기존 토이 프로젝트의 실제 브랜치·그룹·패키지·최근 커밋 메시지를 읽기 전용으로 대조했다.
현재 MstChgMgmt 브랜치는 main, Gradle group은 com.hwandevblog,
Java 기본 패키지는 com.hwandevblog.mstchgmgmt, 진입점은 MstChgMgmtApplication이다.
Java 소스와 테스트 6개 파일의 패키지·경로를 맞추고 진입점 클래스명을 변경했다.
작업 공간은 C:\toyProject\workspace, 프로젝트명은 PascalCase 축약형,
커밋 메시지는 기존 InvMgmt처럼 한국어로 실제 변경 내용을 설명하는 방식을 따른다.
이전 영어 feat: 형식의 권장 메시지 대신 첫 커밋 메시지는
`기준정보 변경 검증 및 리비전 권한 정책 초기 구현`을 사용하면 된다. 실제 커밋은 아직 하지 않았다.

Java 21, Spring Boot 3.5.16, Gradle Wrapper 기반 실행, 표준 src/main/java·src/test/java 구조는 공통이다.
Gradle 8.14.6 및 현재 프로젝트의 Eclipse 우회 설정은 검증한 구성을 유지한다.
향후 기존 방식과 달라야 하는 설계·도구 선택에는 해결할 문제와 검증 근거를 설명한다.
기존 InvMgmt 소스와 설정은 수정하지 않았다.

변경 후 test·bootJar 실행 성공, 56건 통과, 실패·오류 0건.
생성 JAR manifest의 Start-Class가 com.hwandevblog.mstchgmgmt.MstChgMgmtApplication임을 확인했다.
Eclipse에는 F5와 Gradle Refresh를 실행해 브랜치 표시·패키지·진입점 변경을 반영한다.
## 2단계 구현·검증 완료 — 2026-10-15

작업 경로: C:\toyProject\workspace\MstChgMgmt. 브랜치 main, 1단계 커밋 3108111 이후 변경이다.
사용자 요청대로 같은 채팅에서 이어간다. 커밋·push는 수행하지 않았다.

주요 경로:

- src/main/resources/db/migration/V1__create_accounts_catalog_and_drafts.sql: 최초 스키마·권한.
- src/main/resources/application.yml: 루프백 서버, 세션 쿠키, 앱 연결·Hibernate 검증.
- src/main/java/com/hwandevblog/mstchgmgmt/account: 계정·역할 JPA 조회.
- src/main/java/com/hwandevblog/mstchgmgmt/security: 세션 사용자·계정 상태 재확인 필터·Security·인증 조회 API.
- src/main/java/com/hwandevblog/mstchgmgmt/equipment: 현재 장비 조회.
- src/main/java/com/hwandevblog/mstchgmgmt/request: 본인 DRAFT 생성·목록·리비전 상세와 소유권 정책 연결.
- src/main/java/com/hwandevblog/mstchgmgmt/common/ApiExceptionHandler.java: 정책 거부 응답.
- src/test/java/com/hwandevblog/mstchgmgmt/DatabaseBootstrap.java: 관리자/소유자 마이그레이션과 초기 계정·장비.
- src/test/java/com/hwandevblog/mstchgmgmt/LocalApplication.java: 고정 데이터 디렉터리·중복 실행 잠금·시연 실행.
- src/test/java/com/hwandevblog/mstchgmgmt/PostgresIntegrationTest.java: 제한된 앱 연결을 사용하는 실제 PG 기반.
- src/test/java/com/hwandevblog/mstchgmgmt/AccessApiTest.java: 실제 계정 로그인·HTTP 인증/권한·DB 계정 제한 20건.
- src/test/java/com/hwandevblog/mstchgmgmt/PersistentDatabaseTest.java: 실제 PostgreSQL 종료·재시작 1건.
- frontend/src/App.tsx, api.ts, styles.css: 로그인·기준정보·내 요청/상세 화면과 세션/CSRF 요청.
- frontend/src/App.test.tsx, api.test.ts: UI·CSRF 갱신·요청자 ID 미전송·bigint 표시 6건.
- frontend/e2e/access.spec.ts: 실제 서버/Chrome 로그인·저장·새로고침·계정 전환·직접 API 우회 차단 2건.
- frontend/package-lock.json: 고정 npm 의존성. node_modules·dist·캡처는 Git 제외.

DB 이름 catalog_review, 스키마 catalog, 소유자 mcr_owner(NOLOGIN), 앱 로그인 mcr_app.
실제 앱 current_user=mcr_app, DB 생성·슈퍼유저 권한 없음, 계정 UPDATE·DDL·SET ROLE mcr_owner 거부를 확인했다.
JDBC URL에 관리자 user 파라미터가 남지 않도록 사용자 없는 URL을 구성했다.
마이그레이션은 별도 소유자 연결에서 수행하고 앱 자동 Flyway는 껐다. JPA는 validate만 수행한다.
테이블: app_user, user_role, equipment, change_request, change_revision, flyway_schema_history.
초기 계정 7개와 장비 3개는 빈 DB에만 넣는다. 요청과 첫 리비전 생성은 단일 트랜잭션이다.
현재 포인터가 다른 요청 리비전을 가리키는 경우 복합 FK가 거부한다.

실측 검증 결과:

- scripts/dev.ps1 -Tasks test,bootJar: 최종 서버 77건 통과, 실패·오류·건너뜀 0. 프론트엔드 포함 JAR 생성 성공.
- 프론트엔드 npm run build: TypeScript 검사와 Vite 빌드 성공.
- npm run test:run: 6건 통과.
- MCR_BASE_URL=http://127.0.0.1:18081에서 npm run test:e2e: 최종 2건 통과. 설치된 Chrome 사용.
- CSRF 없는/위조 요청, 로그인 전 토큰 재사용, 세션 ID 미회전, 비활성 계정, 역할 회수, 타인 ID 접근, 요청자 ID 조작을 검사했다.
- SUBMITTED/APPLIED 조회 권한은 관리자 DB fixture로만 검증했다. 실제 제출/승인 흐름은 아직 없다.
- 소유권 검사 제거 실험: 대표 테스트 expected 403 / actual 200으로 1건 실패. 원본 해시 일치 복원 후 서버 77건 재통과.
- 실험 로그 .local/verification/ownership-api-guard.log는 Git 제외다.
- 실제 PostgreSQL 재시작에서 금액 17000·버전 1·임시 요청 1개·초기 계정 7개 보존, fsync=on을 확인했다.
- 브라우저 검증 중 화면 전환 직후 입력 폼 재생성으로 사유가 사라지는 문제를 발견했다.
  화면 전환 시 즉시 로딩 상태로 바꾸고 계정 전환 시 목록·상세를 비우도록 수정했다.
  이후 입력 사유·전송 JSON·서버 응답 상세 일치와 다른 요청자 전환 E2E를 통과했다.
- npm install 시 감사 결과 취약점 0이었으며, 이를 지속적인 안전성 보장으로 주장하지 않는다.

실행:

1. frontend에서 npm ci, npm run build.
2. 프로젝트 루트에서 scripts/dev.ps1 -Tasks localRun. Java 21 번들을 탐색하는 기존 방식이다.
3. http://127.0.0.1:18081 접속. 공통 시연 비밀번호 LocalDemo!2026.
4. requester_a/requester_b=요청자, approver/approver_b=승인자, viewer=조회자, dual=이중 역할, inactive=비활성.
5. 개발용 Vite는 frontend에서 npm run dev, http://127.0.0.1:5174. API를 18081로 프록시한다.
6. 종료는 실행 터미널 Ctrl+C. 시연 DB .local/postgres-data는 보존한다.

검증 종료 시 로컬 시연 서버 18081과 시연 DB 15433을 실행해 둔 상태다.
E2E는 연결된 시연 DB에 검증용 DRAFT 요청을 생성한다. 삭제하지 않고 보존했다.
테스트 임시 PostgreSQL DB는 시연 DB와 분리되며 종료 후 정리된다.
현재 로컬 Embedded PostgreSQL은 trust 인증을 사용하는 개발 환경이고 운영 배포/인증 강화는 범위 밖이다.

커밋 대상: build.gradle, README.md, docs/DESIGN.md, docs/HANDOFF.md,
.gitattributes, 표준 줄바꿈 정규화 대상 gradlew.bat, 새 account/common/equipment/request/security 코드,
src/main/resources, 위 테스트 기반·API·재시작 테스트, frontend 소스·설정·package-lock.json.
.gitattributes는 InvMgmt와 같은 wrapper LF·bat CRLF·JAR binary 규칙이다. gradlew.bat의 실행 내용 변경은 없다.
권장 커밋 메시지: 로그인 및 기준정보 조회와 요청 소유권 통제 구현
Eclipse F5 → Gradle Refresh로 새 의존성을 반영하고 Git Staging에서 변경 파일을 확인한 뒤 Commit만 사용한다.
## 확정 디자인 적용 완료

- frontend/src/App.tsx, frontend/src/styles.css: 중앙 로그인 카드와 밝은 녹색 업무 화면, 사용자 표시, 요청 작성 바로가기 적용.
- 기존 인증·API·DB 로직 변경 없음. CSV 업로드 이후 업무 기능은 아직 구현하지 않았다.
- 프론트엔드 build 성공, 기존 UI/API 단위 6건 및 실제 Chrome E2E 2건 통과.
- 추가 브라우저 확인: 로그인 실패 표시, 320px 로그인·기준정보·요청 작성 화면의 문서 가로 넘침 없음, 요청 작성 바로가기 정상, 브라우저 오류 없음.
- 18081의 기존 실행 서버에서 새 리소스 제공을 확인했다. 중복 localRun은 데이터 디렉터리 잠금으로 차단되었으며 기존 서버를 유지했다.
- DB 스키마 변경 없음. 검증용 DRAFT 추가는 기존 E2E 동작이며 보존했다.
- 이전 2단계와 이번 디자인 모두 아직 커밋하지 않았다. 함께 커밋할 경우 권장 메시지: 로그인 및 기준정보 조회와 변경 요청 기본 화면 구현
- 다음 작업은 확정 시안의 CSV 업로드·행 검증·변경 전후 비교 기능이다. 같은 채팅에서 진행한다.


## 3단계 구현·검증 완료 — 2026-10-15

- 신규 서버: src/main/java/com/hwandevblog/mstchgmgmt/request/RevisionWorkspaceService.java, RevisionWorkspaceController.java.
- 변경 서버: 같은 폴더의 ChangeRevisionRepository.java(리비전 잠금), common/ApiExceptionHandler.java(413), src/main/resources/application.yml(Multipart 상한).
- DB 변경: src/main/resources/db/migration/V2__add_revision_workspace.sql. catalog.revision_workspace 추가, 앱 SELECT·INSERT·UPDATE 부여. 기존 V1 변경 없음. 실제 시연 DB에도 V2 적용했다.
- 신규 UI: frontend/src/RevisionWorkspace.tsx, RevisionWorkspace.test.tsx. 기존 App.tsx·App.test.tsx·api.ts·styles.css 연결.
- 신규 API/DB 테스트: src/test/java/com/hwandevblog/mstchgmgmt/RevisionWorkspaceApiTest.java 11건. PersistentDatabaseTest.java에 원본·검증 JSON 보존 확인 추가.
- 신규 Chrome 테스트: frontend/e2e/workspace.spec.ts. 샘플: samples/catalog-change.csv, samples/catalog-invalid.csv. 기존 catalog-create.csv도 사용 가능.
- API 기준 경로 /api/requests/{requestId}/revisions/{revisionId}: POST/GET /file, POST /validation({fileId}), GET /workspace.
- 업로드와 검증은 본인 현재 DRAFT만 허용한다. 조회·다운로드는 VIEW_ORIGINAL 정책을 따른다. 파일 교체 시 UUID 재발급·이전 검증 NULL, 과대 파일 거부 시 기존 데이터 유지.
- 검증 결과: validationId·fileId·rulesVersion·valid·stale, 신규/변경/변경 없음 수, 레코드/필드/코드 오류, 전체 전후 값·기대 버전. 금액·버전은 문자열이다.
- 현재 검증 JSON은 수정 가능한 작업 결과다. 불변 제출본·내용 해시·승인 API·기준정보 반영은 아직 없다. 검증·비교만으로 장비 데이터가 바뀌지 않는 것도 검증했다.
- 서버 test,bootJar 성공: 88건, 실패·오류·건너뜀 0. 프론트엔드 build 성공, test:run 10건, 실제 서버/Chrome E2E 3건 통과.
- API/DB 확인: 원본 바이트·BOM·SHA 보존, bigint 상한 문자열, DB 존재 오류, 타인 역할 접근403, 구 리비전409, 다른 요청 조합404, CSRF, 교체 후 이전 파일 ID409, 기준정보 값·버전 변경·신규 코드 등장 시 stale.
- 실제 HTTP Multipart 1MiB 초과413 및 기존 파일·검증 ID 유지 확인. 오류 수정·파일 재선택·업로드·검증·다운로드·새로고침 복원 확인. 320px 문서 가로 넘침 없음, 비교표 내부 스크롤 확인.
- 기존 터미널 강제 종료 후 PostgreSQL만 남아 있음을 발견했다. 시연 데이터 폴더를 지정한 pg_ctl stop -m fast -w로 정상 종료한 뒤 앱과 DB를 재시작했다. 데이터 디렉터리를 삭제하거나 재생성하지 않았다.
- 실제 DB·앱 재시작 전후 원본 바이트·파일 ID/해시·validationId·전체 비교값·본인 요청 수 일치 확인. .local/verification/workspace-restart.json은 Git 제외한 검증 자료다.
- 최종 시연 서버 18081, PostgreSQL 15433 실행 중. 실행 세션은 56802다. 재접속 시 세션 로그인이 필요할 수 있다. DBeaver 기존 연결은 재연결하면 된다.
- UI 캡처 frontend/test-results/workspace-desktop.png, workspace-mobile-final.png는 Git 제외. Chrome 검증용 DRAFT는 보존했다.
- README·DESIGN·HANDOFF는 이 단계 종료 시 한 번 갱신했다. InvMgmt는 이번 단계에서 변경하지 않았다.
- 3단계 커밋 메시지: CSV 업로드 및 검증과 변경 전후 비교 구현. 이전부터 남아 있던 gradlew.bat 줄바꿈 변경은 이번 단계 대상에서 제외한다.


## 4단계 구현·검증 완료 — 2026-10-15

- 신규 서버: domain/SubmissionContent.java, request/SubmissionService.java, SubmissionController.java. base src/main/java/com/hwandevblog/mstchgmgmt.
- 변경 서버: ChangeRequestRepository.java(요청 잠금), ChangeRevision.java(사유 수정·SUBMITTED 전환), RevisionWorkspaceService.java(제출 원본 우선 다운로드), common/ApiExceptionHandler.java(409 코드 응답).
- DB: src/main/resources/db/migration/V3__add_immutable_submission.sql. catalog.revision_submission, reject_submission_mutation 함수·immutable_submission 트리거. 앱 SELECT·INSERT만 허용. V1/V2는 수정하지 않았다. 실제 시연 DB에도 V3 적용했다.
- 신규 UI: frontend/src/SubmissionPanel.tsx, SubmissionPanel.test.tsx. App.tsx·RevisionWorkspace.tsx·api.ts·styles.css 연결. 사유 저장→제출 전체 내용 확인→확인 체크→제출→고정 제출본·원본 다운로드.
- 신규 테스트: src/test/java/com/hwandevblog/mstchgmgmt/domain/SubmissionContentTest.java 4건, SubmissionApiTest.java 12건, frontend/e2e/submission.spec.ts 1건.
- API 기준 /api/requests/{requestId}/revisions/{revisionId}: POST /reason({reason}), GET /submission-preview, POST /submission({fileId,validationId,contentHash}), GET /submission.
- 준비 응답에는 요청·리비전·요청자·사유·규칙·파일·원본 해시·내용 해시·전체 전후 값·기대 버전이 함께 있다. 제출 성공 시 UUID submissionId·submittedAt이 추가된다.
- 준비 조회는 본인 현재 DRAFT이며 사유·유효 검증·실제 변경·기준정보 일치를 검사한다. 제출 시 동일 검사를 다시 하고 화면이 보낸 fileId·validationId·contentHash와 대조한다. 클라이언트 rows 등 변경값 전송은 400이다.
- 내용 해시 v1 고정 벡터 364바이트 / 16e894d120581b639ea00bb574c3537df77656068acd20e27fa6b4a4664b0f7d. 행 정렬·사유 원문·기대 버전·변경 없음 포함을 검증했다.
- 실제 PG: 동일 동시 제출은 한 영수증, 이미 INSERT한 후 상태 갱신 실패 주입은 DRAFT·제출본 0개로 전체 롤백. 앱 UPDATE·DELETE·TRUNCATE 42501, 소유자 UPDATE·DELETE 트리거 55000 확인.
- 오래된 검증·파일·사유·현재 리비전·기준정보를 거부하고 빈 사유·변경 없음 제출을 거부했다. 다른 요청자·승인자·조회자는 타인 DRAFT 준비·수정·제출 불가. 제출 후 소유자·타인 승인자 조회 허용, 조회자·다른 요청자 제출본 접근 거부.
- workspace 원본을 관리자 fixture로 변경해도 GET /file은 고정 제출본의 원본을 반환했다. 장비 금액·버전은 제출로 변경되지 않았다.
- 최종 서버 test,bootJar 성공: 104건, 실패·오류·건너뜀 0. 프론트엔드 build·test:run 13건, 실제 Chrome E2E 4건 통과.
- Chrome은 표시한 묶음 확인 후 다른 검증 결과로 바꾸는 상황을 직접 만들고, 409 안내·기존 값 유지·재검토·다시 확인한 식별자만 제출·결과 복원을 검증했다.
- 시연 PostgreSQL·앱을 pg_ctl 정상 종료 후 재실행해 submissionId·contentHash·원본 바이트·전체 스냅샷·SUBMITTED 동일성을 확인했다. .local/verification/submission-restart.json은 Git 제외.
- 시연 서버 18081, DB 15433 실행 중. 세션 91437. 새로 로그인하면 제출된 요청 #10의 고정 제출본을 볼 수 있다. 이 번호는 시연 fixture로 환경마다 달라진다.
- 캡처 frontend/test-results/submission-desktop.png, submission-mobile-final.png는 Git 제외. E2E에서 만든 요청은 보존했다.
- 승인·반영·반려·철회·새 리비전·성공 변경 이력·승인 재요청은 아직 미구현이다. 이번 재요청 검증은 SUBMIT만 해당한다.
- 사용자 작업 방식: 커밋 완료 통보 시 로컬 HEAD와 GitHub main을 확인하고 정상 반영이면 별도 확인 없이 다음 단계로 진행한다. 사용자 요청 없이 커밋하지 않는다. push 상태 조회와 실제 push 수행 권한을 구분한다.
- 커밋 메시지: 불변 제출본 및 내용 해시와 변경 요청 제출 구현. 이전 gradlew.bat 줄바꿈 변경은 제외한다.


## 5단계 구현·검증 완료 — 2026-10-15

- 신규 서버: src/main/java/com/hwandevblog/mstchgmgmt/request/DecisionService.java, DecisionController.java.
- 변경 서버: ChangeRevision.java(최종 상태·기반 리비전), ChangeRevisionRepository.java(리비전 목록), common/ApiExceptionHandler.java(409/500 확인 번호·별도 로그).
- DB V4: src/main/resources/db/migration/V4__add_decisions_and_equipment_changes.sql. revision_decision·equipment_change·불변 트리거, based_on_revision_id·동일 요청 FK·UNIQUE, 최소 장비 쓰기 권한. 기존 V1~V3 미변경. 실제 시연 DB V4 적용 완료.
- 신규 UI: frontend/src/WorkflowActions.tsx, RevisionHistory.tsx, WorkflowActions.test.tsx, AppSession.test.tsx.
- 변경 UI: App.tsx·App.test.tsx·SubmissionPanel.tsx·api.ts·styles.css. 승인 대기·반영 이력·목록 갱신·결정·현재/과거 리비전 조회. 조회자의 CSV 원본 버튼은 숨긴다.
- 신규 Chrome: frontend/e2e/zz-workflow.spec.ts 3건. 기존 submission.spec.ts의 완료 안내 문구만 갱신했다.
- 신규 API/DB: src/test/java/com/hwandevblog/mstchgmgmt/DecisionApiTest.java 13건. 전체 서버 117건, 실패·오류·건너뜀 0. test,bootJar 성공.
- 프론트엔드 타입 검사·빌드 성공, 단위/UI 17건 통과. Chrome 총 7건 통과; 최종 새로고침 경로 변경 후 업무 3건을 재확인했다.
- 역할별 API: GET /api/approvals/pending, /api/history/applied; POST /api/requests/{requestId}/approve·reject·withdraw·revisions; GET /api/requests/{requestId}/history 및 /revisions/{revisionId}/decision.
- 결정·새 리비전 body는 revisionId·contentHash, 반려는 reason 필수, 철회 reason은 선택이다. 클라이언트 rows는 400. 액터는 인증 세션만 사용한다.
- 요청→리비전→장비 코드순 잠금, 기대 버전·변경 전 값 대조, CREATE 존재/UNIQUE 검사 후 서버 고정 제출본에서만 적용한다. 장비·실제 변경·영수증·APPLIED가 한 트랜잭션이다.
- 신규 version=0, 실제 UPDATE version+1, 변경 없음은 충돌 검사만 하고 이력·버전 증가 없음. 후행 장비 UPDATE 실패와 마지막 APPLIED UPDATE 실패 주입에서 전체 롤백·SUBMITTED 유지·성공 이력 없음 확인.
- 같은 승인자의 동시 재요청은 한 영수증, 다른 승인자 경합은 한 성공·한 충돌. 신규 코드 경합은 두 트랜잭션을 INSERT 직전에 advisory lock으로 묶어 UNIQUE 최종 방어를 검증했다.
- 재요청 전 역할·활성·자기 결정 금지 재검사, 이중 역할 자기 승인/반려403, 변경 없음 행 버전 충돌409, 잘못된 해시409, 반려 사유 필수, 철회 소유권, APPLIED 재작성 불가, 과거 리비전 결정409 확인.
- 새 DRAFT는 기존 제출 사유·파일을 복사하고 새 fileId·검증 NULL로 생성한다. 동일 새 리비전 재요청은 같은 현재 DRAFT를 반환한다. 이전 REJECTED/WITHDRAWN 제출본·영수증 보존.
- API/DB 오류 traceId는 별도 서버 로그와 응답에만 기록한다. 실패 진단을 성공 이력 테이블에 넣지 않는다.
- 계정 전환 epoch로 지연된 자식 처리 콜백을 폐기한다. 실제 React 테스트에서 가드를 잠시 제거하자 타인 비공개 사유가 다시 표시되어 실패했다. 원본 App.tsx SHA-256 일치 복원 후 전체 UI 17건 재통과. 운영/시연 화면 리소스에는 제거한 코드를 반영하지 않았다.
- 실제 Chrome에서 반려 응답을 서버 처리 후 지연시킨 상태로 로그아웃·다른 요청자 로그인·목록 조회 후 응답을 전달해 타인 상세가 나타나지 않음을 확인했다.
- 목록 갱신은 별도 async 상태 갱신 대신 기존 취소 가능한 조회 effect를 재사용해 계정·페이지 전환의 늦은 목록 응답도 폐기한다.
- 실제 DB·앱 재시작 전후 APPLIED 값·버전·영수증·고정 제출본, REJECTED/WITHDRAWN 이전 제출본과 3번째 DRAFT·원본·검증 상태가 일치했다. .local/verification/workflow-restart.json은 Git 제외.
- 서버 18081·DB 15433 실행 중, 세션 54684. DBeaver는 catalog.revision_decision·equipment_change·revision_submission·change_revision을 조회하면 된다.
- 시연 DB에는 Chrome 검증용 신규 장비·요청·승인/반려/철회 이력이 남아 있다. 초기 장비 값은 Chrome에서 변경하지 않았다. 서버 실패·경합 실험은 별도 임시 DB만 사용했다.
- 캡처 frontend/test-results/approval-desktop.png, applied-history-mobile.png는 Git 제외. Playwright 재실행 시 test-results는 정리되므로 이전 재시작 확인 캡처는 남지 않을 수 있다.
- README·DESIGN·HANDOFF는 단계 완료 시 한 번 갱신했다. InvMgmt와 회사 저장소·DB는 이번 단계에서 수정·조회하지 않았다.
- 커밋 메시지: 기준정보 승인 반영 및 반려·철회와 리비전 이력 구현. 이전 gradlew.bat 줄바꿈 변경은 제외한다.

## 6단계 로컬 구성·검증 완료 — 2026-10-15

- 신규: .github/workflows/backend-ci.yml, frontend-ci.yml, docs/DEMO.md. 변경: README.md, docs/DESIGN.md, docs/HANDOFF.md. 총 6개.
- InvMgmt의 개인 CI 설정을 읽기 전용으로 참고해 동일한 이벤트·도구 버전·명령 양식으로 구성했다. 업무 코드는 복사하지 않았다.
- Node 24에서 npm ci 성공(감사 취약점 0), typecheck 성공, UI 17건 통과, build 성공.
- 기존 서버 테스트 XML 원본에서 117건·실패/오류/건너뜀 0 확인. 서버 소스 변경이 없어 재실행하지 않았다. Chrome도 재실행하지 않았다.
- CI 파일은 기존 InvMgmt 템플릿과 바이트 일치 및 명령·대상 경로를 확인했다. 별도 로컬 YAML 파서 검증은 미실시했으나 이후 GitHub에서 두 워크플로 실제 실행 성공을 확인했다.
- DB 변경 없음. 기존 V1~V4·시연 데이터 보존. 기존 gradlew.bat 줄바꿈 변경은 이번 커밋 대상 제외.
- 커밋 메시지: GitHub Actions 검증 구성 및 시연 안내 정리.

## 원격 CI 확인 완료 — 2026-10-15

- 로컬 HEAD와 GitHub main: cae4afa12bbbff3f30a24d9ab10d458d0b29ad14 일치.
- Backend CI: completed/success. https://github.com/HwanDevBlog/MstChgMgmt/actions/runs/38705508866
- Frontend CI: completed/success. https://github.com/HwanDevBlog/MstChgMgmt/actions/runs/38705508783
- InvMgmt 형식의 CI 배지와 결과 문구를 README·DEMO·DESIGN·HANDOFF에 반영했다. 총 4개 문서이며 앱·DB·워크플로 변경은 없다.
- 실제 원격 실행 결과를 조회했고 로컬 테스트는 반복하지 않았다. gradlew.bat의 기존 줄바꿈 변경은 제외한다.
- 커밋 메시지: README CI 배지 및 검증 결과 반영.

## 이클립스 직접 실행 홈 화면 404 수정 — 2026-10-19

- 원인: Gradle processResources는 frontend/dist를 static으로 복사하지만 Eclipse Java Application은 bin/main만 사용해 해당 복사를 거치지 않았다. dist/index.html은 존재하고 bin/main/static/index.html은 없음을 확인했다.
- 수정: src/test/java/com/hwandevblog/mstchgmgmt/LocalApplication.java에서 로컬 실행에만 frontend/dist의 file URI를 static-locations로 설정한다. 빌드 파일이 없으면 DB를 시작하기 전에 빌드 안내 오류를 반환한다. 패키징과 서버 보안 설정은 변경하지 않았다.
- 신규 LocalFrontendTest.java: 클래스패스 밖 임시 index·CSS·JS의 HTTP 200 및 홈 본문, 익명 API 401, 빌드 누락 안내를 검증한다. 별도 임시 DB를 사용했다. 전체 서버 119건 통과, 실패·오류·건너뜀 0.
- README의 Eclipse 작업 디렉터리·화면 빌드 안내와 테스트 건수를 갱신했다. 실제 사용자 Eclipse 프로세스는 자동 종료하지 않았고 F5 후 LocalApplication 재실행·브라우저 새로고침 확인이 남아 있다.
- DB·마이그레이션 변경 없음. 기존 gradlew.bat 줄바꿈 변경은 제외.
- 커밋 대상: LocalApplication.java, LocalFrontendTest.java, README.md, docs/HANDOFF.md. 메시지: 이클립스 실행 시 로그인 화면 경로 수정. 사용자 화면 확인 후 커밋한다.

## 이클립스 상세 조회 500 수정 — 2026-10-19

- 사용자 요청 #30은 정상 저장됐고 mine API는 200, 상세·이력은 500을 별도 로그인 세션의 읽기 전용 조회로 재현했다.
- javap로 Eclipse bin/main의 RequestController에는 MethodParameters가 없고 Gradle 클래스에는 있음을 확인했다. 암시적 @PathVariable 이름 추론이 Eclipse 컴파일에 의존한 것이 원인이다.
- RequestController·RevisionWorkspaceController·SubmissionController·DecisionController의 경로 변수 26곳에 requestId·revisionId 이름을 명시했다. DB·권한·업무 상태 정책은 변경하지 않았다.
- Git 제외 .local/verification/no-parameter-names.gradle로 -parameters를 제거하고 전체 재컴파일·서버 테스트 119건 통과, 실패·오류·건너뜀 0. 실제 클래스에도 MethodParameters가 없음을 확인했다. 기본 Gradle 설정은 변경하지 않았다.
- 실제 Eclipse 서버는 사용자가 F5 후 LocalApplication 종료·재실행하고 요청 #30 상세를 확인해야 한다. 새 요청을 중복 생성할 필요가 없다.
- 이전 화면 404 수정과 함께 미커밋: LocalApplication.java, LocalFrontendTest.java, 위 Controller 4개, README.md, docs/HANDOFF.md 총 8개. gradlew.bat은 제외한다. 커밋 메시지: 이클립스 실행 시 화면 및 요청 상세 조회 오류 수정. 화면 확인 후 커밋 안내한다.

## Eclipse PostgreSQL 로그 깨짐 설정 완료 — 2026-10-19

- InvMgmt의 개인 build.gradle localRun은 LC_ALL=C·LANG=C를 사용하고, 기존 MstChgMgmt Java Application 실행 구성에는 환경 변수가 없음을 확인했다. InvMgmt 파일은 수정하지 않았다.
- 프로젝트 루트 MstChgMgmt - localRun.launch를 추가했다. Java Application LocalApplication, 프로젝트 루트 작업 디렉터리, LC_ALL=C·LANG=C, UTF-8 콘솔 설정을 포함한다. 사용자 Eclipse 메타데이터를 직접 수정하지 않았다.
- 같은 프로세스 환경에서 실제 pg_ctl status를 실행해 영어 출력과 기존 PID 45388 실행 상태를 확인했다. DB 중지·삭제·재시작은 하지 않았다. XML 파싱·환경 값과 diff 형식 검증 완료. 앱 코드 변경이 없어 전체 테스트는 반복하지 않았다.
- README 실행 안내를 전용 공유 구성 사용으로 변경했다. Eclipse F5 후 Run Configurations > Java Application > MstChgMgmt - localRun을 선택해야 한다. 기존 LocalApplication 실행 구성은 새 환경 변수를 자동 상속하지 않는다.
- postmaster.pid already exists는 이전 PostgreSQL 잔류로 인한 별도 중복 기동 메시지다. 인코딩 수정으로 해결됐다고 주장하지 않는다. 현재 DB는 그대로 보존했다.
- 앞선 Eclipse 화면·API 수정과 합쳐 커밋 대상 9개: launch, README, HANDOFF, LocalApplication, LocalFrontendTest, Controller 4개. gradlew.bat 제외. 권장 메시지: 이클립스 실행 시 화면·조회 오류 및 로그 설정 수정.

## 로컬 요청자 계정 변경 완료 — 2026-10-19

- 사용자 요청으로 로컬 requester_a를 tester / 1로 변경했다. 계정 ID 1·REQUESTER 역할·기존 요청 소유권은 유지한다. 나머지 계정과 자동 서버 테스트용 기본 seed는 변경하지 않았다.
- DatabaseBootstrap.configureLocalTester를 LocalApplication에서만 호출한다. 기존 DB와 신규 DB에 모두 적용하며 BCrypt 해시를 저장한다. 이미 tester/1이면 해시를 다시 쓰지 않는다. 두 계정이 동시에 존재하면 임의 병합하지 않고 중단한다.
- 현재 시연 DB에도 변경 적용 완료. 실제 HTTP 로그인 204, me username=tester/id=1, mine에 요청 #30 유지 확인. 기존 사용자 브라우저는 로그아웃 후 tester/1로 재로그인한다. DB 마이그레이션·장비·요청 내용 변경 없음.
- 별도 임시 PG의 LocalDemoAccountTest 1건 통과: ID·요청 소유권·역할 보존, 비밀번호 BCrypt 일치, 반복 적용 시 해시 불변 확인. 이전 119건 전체 테스트는 이번 계정 변경 후 반복하지 않았다.
- README·DEMO 계정 안내와 E2E 4개 파일의 요청자 로그인만 새 정보로 맞췄다. Playwright --list에서 7건 정상 로드. 실제 E2E 전체 재실행은 하지 않았다.
- 앞선 Eclipse 수정 포함 미커밋 16개(gradlew.bat 제외). 계정 관련 신규 변경: DatabaseBootstrap.java, LocalDemoAccountTest.java, docs/DEMO.md, frontend/e2e/*.spec.ts 4개. LocalApplication·README·HANDOFF는 기존 변경에 추가했다.
- 통합 커밋 메시지: 이클립스 실행 오류 수정 및 로컬 테스트 계정 변경. 사용자가 화면 확인 후 Eclipse에서 직접 커밋한다.

## 전체 UI 일괄 개선·검수 완료 — 2026-10-19

- 대상: frontend/src/App.tsx, RevisionWorkspace.tsx, RevisionHistory.tsx, SubmissionPanel.tsx, WorkflowActions.tsx, styles.css. 회귀 검증: RevisionWorkspace.test.tsx. README·DEMO·DESIGN·HANDOFF는 완료 후 한 번 정리했다.
- 전체 표의 열 너비·헤더/본문 정렬, 공통 글자·버튼·상태 배지·포커스, 장문 사유 줄바꿈, 모바일 내부 스크롤을 정리했다. 실제 처리 이력의 금액 헤더와 값도 오른쪽 정렬한다.
- 긴 목록 max-height·고정 헤더, 상세 선택 시 스크롤·포커스, 검증 직접 실행 후 결과 이동·완료/오류 안내를 추가했다. 업로드된 파일 표시와 파일 선택/업로드/검증 단계 안내·처리 중 문구를 구분한다. 선택한 과거 리비전은 현재 리비전으로 잘못 표시하지 않는다.
- 최종 프론트엔드 전체 18건·타입 검사·Vite 빌드 통과. 사용자 혼동 경로의 회귀 테스트는 오류 검증 후 결과 포커스·오류 건수 안내·검증 API 한 번 호출을 확인한다.
- 실제 Chrome E2E 7건 통과: 로그인/소유권, 오류 CSV 수정, 검토 묶음 충돌, 승인/반영, 반려/새 리비전/철회, 계정 전환 지연 응답. 최종 버튼 공통 CSS·문구 소폭 정리 후에는 빌드·화면 검수만 했으며 E2E 전체를 불필요하게 반복하지 않았다.
- 화면 검수: 로그인·기준정보·요청 목록·임시 검증·제출 검토·반려 상세·승인 목록/검토·반영 목록/상세·과거 철회 상세 11종은 실제 API/DB로 조회했다. 오류·빈 목록 2종은 해당 검수 브라우저의 GET 응답만 대체했다. 각각 1440px·390px 총 26캡처, 표 정렬·페이지 가로 넘침·브라우저 오류 검사 0건. 실제 캡처도 확인했다.
- 검수 결과와 캡처: C:\toyProject\workspace\MstChgMgmt\.local\verification\ui-review\report.json 및 같은 폴더의 PNG. Git 제외다. 검수 스크립트는 C:\Users\USER\Documents\ChatGPT\기타\.cache\mcr-ui-audit.mjs다.
- 사용자 요청 #30은 조회·제출 미리보기만 수행해 DRAFT·정상 CSV 검증 결과를 유지했다. E2E는 별도 신규 요청·장비에 시연 이력을 남겼고 초기 3개 장비 값은 유지했다. 이번 UI 작업에는 DB 마이그레이션·서버 권한·승인 정책 변경이 없다.
- 커밋 대상 24개(기존 gradlew.bat 제외): 문서 README.md·docs/DEMO.md·docs/DESIGN.md·docs/HANDOFF.md 4개, 공유 launch 1개, frontend/e2e 4개, frontend/src 위 7개, Java RequestController·RevisionWorkspaceController·SubmissionController·DecisionController·DatabaseBootstrap·LocalApplication·LocalDemoAccountTest·LocalFrontendTest 8개.
- 통합 커밋 메시지: 이클립스 실행 오류 수정 및 테스트 계정과 화면 UI 개선.

## 로컬 두 계정 정리 완료 — 2026-10-19

- 사용자 확정: 요청자 tester/1 하나, 관리(승인) admin/1 하나만 로그인 허용. tester ID=1·REQUESTER, admin ID=3·APPROVER. 기존 이름 requester_a·approver는 각각 변경했고 requester_b·approver_b·viewer·dual·inactive는 비활성 상태다. 기존 사용자 행과 역할은 과거 참조를 위해 보존했다.
- DatabaseBootstrap.configureLocalAccounts가 두 계정 이름·비밀번호·활성을 정리하고 나머지를 비활성화한다. LocalApplication의 기존 configureLocalTester 호출을 대체했다. 기본 테스트 seed는 변경하지 않아 다중 요청자·승인자·조회자·복수 역할 API 테스트는 별도 DB에서 유지한다.
- 실제 시연 DB 적용 전후 요청·제출본·결정·장비 변경 이력·사용자 행 수, 요청 #30 소유자와 제출 내용 해시가 같음을 확인했다. 요청 #30은 승인 대기 그대로이며 승인/반려하지 않았다. 기존 영수증의 과거 사용자명 텍스트는 재작성하지 않는다.
- 실제 HTTP: tester/1·admin/1 로그인 204, requester_a·approver·requester_b·approver_b·viewer·dual·inactive 로그인 401. 변경 전 로그인한 viewer 세션도 변경 후 me 401을 확인했다.
- LocalDemoAccountTest 최신 2건 통과: 두 활성 계정·ID·요청 소유권·APPROVER 역할·해시 보존과 반복 적용, 중복 admin 계정 시 부분 변경 롤백. 전체 서버 테스트는 이번 로컬 fixture 변경에서 반복하지 않았다.
- Chrome E2E 7건 모두 통과. access.spec.ts는 admin의 DRAFT 상세 403·요청 생성 403을 검사하고, zz-workflow.spec.ts는 tester/admin만으로 제출·승인·새 세션 이력·반려·새 리비전·철회·계정 전환 지연 응답 폐기를 확인한다. 사용자 요청 #30 대신 새 QA 요청·장비를 사용했다.
- 변경 파일 9개: src/test/java/com/hwandevblog/mstchgmgmt/DatabaseBootstrap.java·LocalApplication.java·LocalDemoAccountTest.java, frontend/e2e/access.spec.ts·zz-workflow.spec.ts, README.md·docs/DEMO.md·docs/DESIGN.md·docs/HANDOFF.md. 기존 gradlew.bat은 제외한다.
- 커밋 메시지: 로컬 요청자 및 승인자 계정 단순화. 현재 서버에도 적용했으므로 로그아웃 후 admin/1로 로그인하면 된다. 다음 Eclipse 실행 전 F5로 소스를 반영한다.

## 상세 보기 하단 이동 오류 수정 완료 — 2026-10-19

- 요청 #30 실제 재현: 최초 상세·로딩 완료·같은 리비전 재선택 모두 제목 y=-1315px. ID 변경 때만 이동하던 로직과 비동기 렌더링의 브라우저 자동 스크롤 보정이 겹쳤다.
- App.tsx: 상세 묶음 wrapper, detailVisit으로 같은 ID 재선택 감지, useLayoutEffect에서 제목 이동·포커스. styles.css: 최소 뷰포트 높이 확보와 앱 내부 scroll anchoring 차단. 중간 수정의 최초 이동 오류를 새 위치 E2E가 실제로 실패시켜 검출했다.
- 최종 실제 요청 #30: 데스크톱 1440x950의 최초·로딩 완료·동일 리비전 재선택 제목 y=40.89px, 모바일 390x844의 세 경우 y=37.69px. 사용자 요청은 조회만 했고 APPLIED와 승인 이력은 유지했다.
- 프론트엔드 전체 18건·타입 검사·빌드 통과. zz-workflow.spec.ts에 최초 열기·같은 리비전·같은 요청 보기의 제목 y=0~120px 검증을 추가하고 관련 Chrome 업무 3건을 최종 통과했다. 서버 전체는 UI 수정이라 반복하지 않았다.
- 이번 UI 파일은 frontend/src/App.tsx·styles.css, frontend/e2e/zz-workflow.spec.ts. 앞선 두 계정 정리와 합쳐 커밋 대상 11개(gradlew.bat 제외): 문서 4개, Java 3개, E2E 2개, App.tsx·styles.css 2개.
- 통합 커밋 메시지: 로컬 계정 정리 및 상세 보기 스크롤 수정. 다음 확인: Ctrl+F5 → 반영 이력 #30 보기 → 요청 #30 상세 제목이 화면 위쪽에 보이는지 확인. 활성 계정은 tester/1·admin/1이다.
