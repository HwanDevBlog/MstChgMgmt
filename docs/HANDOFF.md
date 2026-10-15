# 작업 인계 — 3단계 완료 (2026-10-15)

## 확정된 작업 경로·상태

- 프로젝트: `C:\toyProject\workspace\MstChgMgmt`
- 로컬 Git 브랜치: `main`. 최근 커밋 `6134c06`(README 양식 변경), 원격 https://github.com/HwanDevBlog/MstChgMgmt.git. 3단계 변경은 미커밋·미push.
- 1단계 완료: 독립 CSV 규칙, 권한표, 상태 전이, 구현과 단위 테스트.
- 2단계 완료: 세션 로그인·CSRF, PostgreSQL/Flyway, 조회·본인 임시 요청 생성과 API 소유권 통제, React 화면. 전체 MVP는 미완료.
- 3단계 완료: CSV 업로드·원본 보존·DB 존재 검증·전후 비교·기준정보 변경 감지. 제출·승인은 미구현.
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

**4단계: 불변 제출본·내용 해시·제출**을 같은 채팅에서 진행한다.

1. docs/DESIGN.md의 길이 접두사 바이너리 v1 직렬화 계약을 읽고 고정 해시 벡터를 검증한다.
2. fileId·validationId와 사용자가 본 비교값을 대조하고 유효 검증·사유·실제 변경 존재를 검사한다.
3. 제출 시점의 기준정보 변경을 재검사하고 제출본을 별도 구조에 고정한다. 기존 DRAFT 작업 결과와 불변 제출본을 구분한다.
4. 확정한 제출 확인 화면에 고정된 이유·파일·전후 값·기대 버전과 제출 ID/hash를 연결한다.
5. 승인·반영은 후속 단계이며 DB 잠금·UNIQUE 경합·전체 롤백·응답 유실 재요청 검증을 생략하지 않는다.

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
