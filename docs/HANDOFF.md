# 1단계 인계 — 2026-10-14

## 확정된 작업 경로·상태

- 프로젝트: `C:\toyProject\workspace\MstChgMgmt`
- 로컬 Git 브랜치: `main`. 저장소 초기화만 완료, 커밋·원격 없음.
- 1단계 완료: 독립 CSV 규칙, 권한표, 상태 전이, 구현과 단위 테스트.
- 전체 MVP는 미완료. 로그인·HTTP API·React·DB는 다음 단계다.
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

## 주요 파일

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

## 검증 결과

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

DB 생성·역할 생성·Flyway 마이그레이션·스키마 변경 없음.
현재 valid()는 CSV 형식·필드 오류 여부만 뜻한다. DB 존재 검사·변경 존재·기대 버전 확인을 대신하지 않는다.
서버 정책 연결, 원본 저장, 제출 내용 DB 보호, 내용 해시 구현, 승인 트랜잭션, PostgreSQL 통합 테스트,
HTTP 권한·CSRF, 동시성, 브라우저 E2E, 시연 데이터 재시작 보존, GitHub Actions는 미구현이다.
프론트 의존성 버전과 DB 실행 구성은 아직 확정·설치하지 않았다.

## 다음 작업

**2단계: 로그인·기준정보 조회·소유권 통제**를 진행한다.

1. 이 문서와 DESIGN.md를 읽고 실제 소스·wrapper 결과를 필요한 범위에서 재확인한다.
2. Spring Security 세션 로그인·CSRF, 인증 사용자 기반 행위자, 활성 여부 확인을 연결한다.
3. Flyway 스키마 소유자·앱 계정 분리와 초기 역할별 계정을 만든다.
4. Docker 없는 실제 PostgreSQL 테스트 구성 및 시연용 고정 데이터 경로를 분리한다.
5. 기준정보 목록·본인 요청 목록/상세를 API로 제공하고 직접 호출 권한 테스트를 작성한다.
6. React·TypeScript 로그인·기준정보·내 요청 조회 화면을 연결한다.
7. 해당 단계 검증 완료 후 문서를 한 번 갱신한다.

사용자 요청에 따라 채팅을 나누지 않고 현재 채팅에서 구현을 이어간다.

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