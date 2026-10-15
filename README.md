# MstChgMgmt — 장비 카탈로그 기준정보 변경·검증·승인

Master Change Management의 축약명. 기존 InvMgmt와 같은 명명 방식으로 정했다.
가상 장비 대여업체의 카탈로그 변경을 검토하고 승인하는 토이 프로젝트다.
현재 **2단계: 세션 로그인·기준정보 조회·본인 임시 요청 생성/조회·소유권 통제**까지 구현했다.

## 보장하려는 규칙

최종 목표는 승인 요청이 참조한 제출본과 실제 DB 반영 내용의 일치다.
제출본 고정, 구 리비전 승인 차단, 자기 승인 금지, 서버 권한 검사로 검증할 예정이다.

현재 코드와 테스트가 확인하는 범위:

- UTF-8·BOM, 1MiB·200레코드 제한, 고정 CSV 필드·파일 내 중복 검증과 원본 바이트 SHA-256.
- 활성 계정·역할·소유권·현재 리비전·상태를 조합한 도메인 정책.
- 세션 로그인·로그아웃·CSRF 검사. 로그인 후 세션 ID와 CSRF 토큰 갱신.
- 비활성 계정 차단, 로그인 이후 비활성화·역할 회수의 다음 API 요청 반영.
- 세 역할의 현재 기준정보 조회, 요청자의 본인 DRAFT 생성·목록·상세 조회.
- 다른 요청자의 ID로 상세를 조회하면 403. 입력 requesterId를 보내면 400.
- 스키마 소유자와 앱 DB 계정 분리, 실제 앱 연결 사용자의 권한 제한.
- 시연 DB 종료·재시작 후 기준정보·버전·임시 요청 보존 및 초기 계정 중복 생성 방지.

자기 승인·구 리비전 차단은 현재 도메인 정책 테스트다. 승인 API·제출본·내용 해시는 아직 구현하지 않았다.

## 설계 판단

독립적인 가상 업무 규칙과 후속 제출본 계약은 [설계 문서](docs/DESIGN.md)에 정의했다.
InvMgmt처럼 main 브랜치·com.hwandevblog 그룹·한국어 주석과 커밋 메시지를 사용한다.
Java 21·Spring Boot 3.5.16·JPA·Gradle Wrapper, React 18.3.1·TypeScript 7.0.2·Vite 8.2.2를 공통으로 사용한다.

Spring Security의 기본 세션 CSRF 저장소를 유지하고 조회 API에서 받은 마스킹 토큰을 변경 요청에 보낸다.
각 변경 요청 전에 토큰을 조회해 로그인·로그아웃 뒤 폐기된 토큰을 재사용하지 않는다.
HTTP URL 권한 검사와 서비스의 객체 소유권 검사를 함께 적용한다.
계정 상태·역할을 API 권한 검사 전에 다시 읽어 세션의 과거 권한으로 작업하지 않게 한다.

CSV의 따옴표·쉼표는 Commons CSV로 파싱한다. 빈칸이나 대소문자를 자동 보정하지 않는다.
금액·버전은 JSON 문자열로 전달하고 화면에서 bigint로 금액을 표시해 정수 정밀도를 보존한다.
요청과 첫 DRAFT 리비전은 한 트랜잭션에서 생성하며, 현재 포인터가 다른 요청의 리비전을 가리키는 경우 DB FK가 차단한다.

마이그레이션은 소유자 권한 연결에서 수행하고 앱 연결의 Hibernate는 스키마 검증만 한다.
Docker 없는 테스트 DB는 임시 디렉터리를 사용하고, 시연 DB는 프로젝트 `.local/postgres-data`를 보존한다.

## 재현 절차

Java 21 JDK, Node 24, npm이 필요하다. Docker Desktop이나 별도 PostgreSQL 설치는 필요하지 않다.
첫 실행에는 공식 Gradle 배포본·Maven Central·npm 패키지 다운로드를 위한 네트워크가 필요하다.

Windows PowerShell:

```powershell
cd C:\toyProject\workspace\MstChgMgmt\frontend
npm ci
npm run build
cd ..
.\scripts\dev.ps1 -Tasks localRun
```

[로컬 화면](http://127.0.0.1:18081)에 접속한다. 종료는 실행 터미널에서 Ctrl+C를 누른다.
서버는 127.0.0.1:18081, 시연 PostgreSQL은 127.0.0.1:15433에서 실행한다.
같은 데이터 디렉터리의 중복 실행은 파일 잠금으로 차단한다.

이 PC에서 npm이 PATH에 없다면 `C:\toyProject\tools\node`를 현재 터미널 PATH에 추가한다.
dev.ps1은 JAVA_HOME 또는 PATH의 javac를 사용하고, 없으면 Eclipse의 Java 21 번들을 찾는다.
다른 PC에서는 JAVA_HOME을 Java 21 JDK로 지정한다. 글로벌 설정은 바꾸지 않는다.

초기 시연 계정의 공통 비밀번호는 **`LocalDemo!2026`**이다.

| 계정 | 역할 |
|---|---|
| requester_a, requester_b | 요청자 |
| approver, approver_b | 승인자 |
| viewer | 조회자 |
| dual | 요청자 + 승인자 |
| inactive | 비활성 계정, 로그인 차단 확인용 |

초기 장비는 EQ-CAM100, EQ-MIC100, EQ-LGT100 세 개다.
요청자 계정으로 로그인하면 임시 요청을 만들고 조회할 수 있다.
계정·장비 초기 데이터는 빈 DB에 한 번만 넣고 재시작 시 사용자 변경값을 덮어쓰지 않는다.
샘플 CSV [samples/catalog-create.csv](samples/catalog-create.csv)는 다음 업로드 단계에서 사용한다.

프론트엔드 개발 서버를 별도로 쓰려면 백엔드 실행 후 frontend에서 `npm run dev`를 실행한다.
127.0.0.1:5174의 Vite가 `/api`를 백엔드로 프록시하므로 별도 CORS 허용이 필요하지 않다.
프론트엔드 빌드 후 `test,bootJar`로 JAR을 만들면 정적 화면도 함께 포함된다.
일반 JAR 실행은 이미 준비된 DB를 필요로 하며, Embedded PostgreSQL은 테스트/로컬 실행기에만 포함된다.

Eclipse에서는 Gradle Wrapper·Java 21·프로젝트별 Gradle user home `.gradle-user-home`을 사용한다.
이 Windows PC에서 확인한 캐시 이동 오류의 우회를 위해 Gradle Arguments와 JVM Arguments에
`-Dorg.gradle.internal.instrumentation.agent=false`를 지정한다.
로컬 `.settings/org.eclipse.buildship.core.prefs`에 적용했으며 `.settings`는 커밋하지 않는다.
설정이나 의존성 변경 후 F5 → Gradle → Refresh Gradle Project를 실행한다.
Eclipse에서 직접 실행할 때는 테스트 소스의 `LocalApplication`을 Java Application으로 실행한다.

## 테스트·DB 결과

검증일: 2026-10-15. 확정된 결과와 다음 작업은 [인계 문서](docs/HANDOFF.md)에 기록했다.

```powershell
.\scripts\dev.ps1 -Tasks test,bootJar
cd frontend
npm run test:run
# localRun 또는 Vite가 실행 중이어야 한다. 설치된 Chrome을 사용한다.
$env:MCR_BASE_URL = 'http://127.0.0.1:18081'
npm run test:e2e
```

| 구분 | 검증 결과 |
|---|---|
| CSV·권한·상태 단위 테스트 | 56건 통과 |
| 실제 PostgreSQL 연결 HTTP 권한·인증 테스트 | 20건 통과 |
| 실제 PostgreSQL 종료·재시작 보존 테스트 | 1건 통과 |
| 프론트엔드 UI·CSRF 전송·bigint 표시 테스트 | 6건 통과 |
| 설치된 Chrome에서 실제 서버에 연결한 E2E | 2건 통과 |
| 소유권 검사 제거 실험 | 타인 상세 조회가 200이 되어 403을 기대한 대표 API 테스트 실패. 복원 후 전체 서버 테스트 통과 |
| DB 사용자·권한 | mcr_app, 비슈퍼유저·DB 생성 불가. 계정 UPDATE·스키마 DDL·소유자 역할 전환 거부 |
| 시연 DB 보존 | 변경한 금액·버전·임시 요청 유지, 초기 계정 7개 유지, fsync=on 확인 |

서버 보고서: `build/reports/tests/test/index.html`.
브라우저 캡처: `frontend/test-results/requester-desktop.png`, `viewer-mobile.png`.
보고서·캡처·DB·캐시·bin은 커밋하지 않는다.

## 한계

현재는 로그인·조회·임시 요청 생성까지 제공한다. CSV 업로드·DB 존재 검증·전후 비교·제출본 고정·승인·반영·이력은 후속 단계다.
SUBMITTED/APPLIED 조회 권한 테스트의 상태는 DB fixture이며 실제 제출·승인을 구현했다는 증거가 아니다.
락·UNIQUE 경합·전체 반영 롤백·승인 응답 유실 재요청·화면 제출 묶음 일치·GitHub Actions는 아직 검증하지 않았다.
파일 상한은 성능 측정값이 아니다. 측정하지 않은 성능·안정성·운영 효과를 주장하지 않는다.
내용 해시는 전자서명이나 읽음 증명이 아니다. 동일인이 여러 계정을 쓰는 것은 통제 범위 밖이다.
Embedded PostgreSQL은 로컬 개발용 trust 인증 환경이다. 앱 계정 권한 제한이 로컬 관리자 접근까지 막는다는 뜻은 아니다.
운영 배포·원격 저장소 생성·push·외부 공개는 진행하지 않았다.
