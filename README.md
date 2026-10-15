# MstChgMgmt

장비 기준정보의 일괄 변경 과정에서 발생하는 CSV 검증, 변경 요청의 소유권과 승인 대상의 일치 문제를 다루는 토이 프로젝트입니다.

단순 CRUD보다 다음 문제를 코드와 테스트로 확인하는 데 초점을 맞췄습니다.

- 잘못된 형식이나 중복 장비 코드가 포함된 CSV를 찾아내는가
- 로그인한 사용자만 자신의 변경 요청을 작성하고 조회할 수 있는가
- 계정이 비활성화되거나 역할이 회수되면 기존 세션의 접근도 차단되는가
- 승인자가 검토한 제출본과 실제 DB에 반영되는 내용이 일치하는가

현재는 세션 로그인, 기준정보 조회, 본인 임시 요청 생성·조회와 CSV 업로드·검증·변경 전후 비교까지 구현했습니다. 제출·승인 API와 불변 제출본은 후속 구현 항목입니다.

## 구현 범위

- 세션 기반 로그인·로그아웃과 CSRF 검사
- 로그인 시 세션 ID 갱신과 로그인·로그아웃 후 CSRF 토큰 갱신
- 비활성 계정 차단과 API 요청 시 계정 상태·역할 재확인
- 요청자·승인자·조회자의 현재 장비 기준정보 조회
- 요청자의 본인 임시 요청 생성·목록·상세·리비전 조회
- 서버에서 인증 사용자와 요청 소유권 검사
- 본인 현재 임시 리비전의 CSV 업로드·원본 보존·다운로드
- CSV 형식·필드·파일 내 중복 검증과 원본 바이트 SHA-256 계산
- CREATE 존재·UPDATE 미존재 검사와 신규·변경·변경 없음 분류
- 필드별 변경 전후 값·기대 버전 저장과 기준정보 변경 감지
- 파일 교체 시 기존 검증 결과 폐기
- 리비전 상태·역할·소유권에 따른 도메인 접근 정책
- 스키마 소유자와 앱 DB 계정의 권한 분리
- 종료·재실행 후에도 유지되는 로컬 PostgreSQL 데이터
- React 기반 로그인·기준정보·내 변경 요청 화면

## 핵심 설계

### 변경 요청과 리비전

`ChangeRequest`는 작성자와 현재 리비전을 가리키고, `ChangeRevision`은 리비전 번호, 상태, 변경 사유를 보관합니다. 요청과 첫 `DRAFT` 리비전은 하나의 트랜잭션에서 생성합니다.

현재 리비전 포인터에는 요청 ID와 리비전 ID를 함께 검사하는 복합 외래 키를 적용했습니다. 다른 요청의 리비전이 현재 리비전으로 지정되는 경우 DB에서 차단합니다.

도메인 정책에서 정의한 상태 흐름은 다음과 같습니다.

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> SUBMITTED: 제출
    SUBMITTED --> APPLIED: 승인과 DB 반영
    SUBMITTED --> REJECTED: 반려
    SUBMITTED --> WITHDRAWN: 철회
```

반려·철회한 리비전은 보존하고 다시 작성할 때 새 `DRAFT` 리비전을 생성하도록 설계했습니다. `APPLIED`는 최종 상태입니다. 현재 API는 첫 `DRAFT` 생성까지 제공하며, 제출·승인·반려·철회·새 리비전 생성은 아직 연결하지 않았습니다.

### 인증과 객체 접근 권한

- 사용자 ID는 인증된 세션에서 가져옵니다. 요청 본문으로 작성자를 지정할 수 없습니다.
- URL별 역할 검사와 서비스의 객체 소유권 검사를 함께 적용합니다.
- 다른 요청자의 임시 요청을 조회하면 `403`, 생성 요청에 `requesterId`를 보내면 `400`으로 처리합니다.
- API 권한 검사 전에 계정 상태와 역할을 DB에서 다시 읽어 세션의 과거 권한으로 접근하지 않게 합니다.
- 화면 메뉴는 역할에 맞게 표시하고, 계정 전환·인증 만료 시 이전 목록과 상세 데이터를 비웁니다.
- Spring Security의 세션 CSRF 저장소를 사용하며, 프론트엔드는 변경 요청 전에 마스킹 토큰을 새로 조회합니다.

자기 승인과 구 리비전 접근 차단은 현재 도메인 정책 테스트로 확인했습니다. 승인 API에서의 통제는 후속 구현·검증 항목입니다.

### CSV 검증과 정수 정밀도

Commons CSV로 따옴표와 쉼표를 처리하며, 빈칸이나 대소문자를 자동 보정하지 않습니다. UTF-8과 BOM, 1MiB·200레코드 제한, 고정 헤더, 필드 형식과 파일 내 중복 장비 코드를 검증합니다.

CSV 원본 바이트의 SHA-256은 파일 식별에 사용합니다. 승인할 변경 내용을 고정하는 내용 해시는 별도 계약이며 아직 구현하지 않았습니다.

장비 금액과 버전은 JSON 문자열로 전달합니다. 프론트엔드는 `bigint`로 금액을 표시해 JavaScript 숫자 변환으로 정수 정밀도가 손실되는 것을 막습니다.

### 파일 교체와 검증 결과

리비전별 `revision_workspace`에 CSV 원본 바이트, 파일 ID·해시와 검증 결과를 저장합니다. 파일을 교체하면 파일 ID를 새로 발급하고 이전 검증 결과를 같은 트랜잭션에서 폐기합니다. 검증 요청이 보낸 파일 ID와 현재 저장된 파일이 다르면 `409`로 거절합니다.

검증 시 기준정보를 한 번에 조회해 신규·변경·변경 없음을 분류하고, 변경 전후 값과 기대 버전을 저장합니다. 파일 전체의 오류가 없어야 검증 완료로 표시합니다. 검증 자체는 장비 기준정보를 변경하지 않습니다.

결과를 다시 조회할 때 저장된 변경 전 값·버전과 현재 기준정보를 비교합니다. 화면의 `최신 상태 확인` 버튼이나 상세 재조회에서 차이가 발견되면 이전 비교값은 유지하고 재검증 필요를 표시합니다. 검증 직후 기준정보가 계속 고정된다는 의미는 아니며, 제출·승인 시점의 재검사는 후속 구현 항목입니다.

### DB 계정과 로컬 데이터 보존

- Flyway 마이그레이션은 별도 소유자 권한 연결로 처리합니다.
- 앱은 제한된 `mcr_app` 계정으로 접속하고 Hibernate `validate`로 스키마를 검사합니다.
- 앱 계정의 계정 정보 수정, 스키마 DDL과 소유자 역할 전환은 허용하지 않습니다.
- 테스트 DB는 임시 디렉터리, 시연 DB는 프로젝트의 `.local/postgres-data`를 사용합니다.
- 시연 DB는 데이터 삭제를 끄고 `fsync`를 켜며, 같은 디렉터리의 중복 실행은 파일 잠금으로 차단합니다.

후속 제출본 계약과 가상 업무 규칙은 [설계 문서](docs/DESIGN.md)에 정리했습니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Backend | Spring Boot 3.5, Spring MVC, Spring Data JPA, Spring Security |
| Query | Hibernate, JdbcTemplate |
| Database | PostgreSQL 16, Flyway |
| CSV | Apache Commons CSV |
| Test | JUnit 5, AssertJ, Spring Boot Test, Zonky Embedded PostgreSQL, Vitest, Testing Library, Playwright |
| Frontend | React 18, TypeScript, Vite |
| Build / CI | Gradle Wrapper, npm / GitHub Actions 미구성 |

## 실행 방법

Java 21과 Node.js 24가 필요합니다. 별도의 PostgreSQL이나 컨테이너 없이 로컬 실행용 임베디드 PostgreSQL이 함께 시작됩니다. 최초 의존성 설치에는 네트워크가 필요합니다.

Windows에서 전체 서버 테스트 실행:

```powershell
.\scripts\dev.ps1 -Tasks test
```

프론트엔드 의존성 설치·빌드와 애플리케이션 실행(프로젝트 루트 기준):

```powershell
cd frontend
npm ci
npm run build
cd ..
.\scripts\dev.ps1 -Tasks localRun
```

`localRun`은 최초 실행 시 시연 계정 7개와 장비 3개를 생성합니다. 이후에는 기존 요청과 장비 값을 보존하고 초기 데이터를 덮어쓰지 않습니다. 종료는 실행 터미널에서 `Ctrl+C`를 사용합니다. DB·캐시·빌드 결과는 Git에서 제외합니다.

`dev.ps1`은 `JAVA_HOME` 또는 PATH의 JDK를 사용하며, 이 PC에서는 Eclipse의 Java 21 번들도 탐색합니다. 다른 PC에서는 `JAVA_HOME`을 Java 21 JDK로 지정하세요. Windows Gradle 캐시 이동 오류를 우회하는 옵션을 실행 프로세스에만 적용합니다.

DBeaver 연결 설정(애플리케이션 실행 중):

| 항목 | 값 |
|---|---|
| 연결 이름 | MstChgMgmt 로컬 |
| Host | 127.0.0.1 |
| Port | 15433 |
| Database | catalog_review |
| Username | mcr_app |
| Password | local-app-only |
| Schema | catalog |

이 접속 정보는 로컬 개발용 Embedded PostgreSQL 구성입니다. 루프백 주소에만 바인딩하며, 로컬 DB는 trust 인증을 사용합니다. 앱 계정의 권한 제한이 로컬 관리자 접근까지 막는다는 뜻은 아닙니다. 자동 테스트는 별도의 임시 DB를 사용합니다.

실행 후 확인할 수 있는 주소:

- 웹 화면: `http://127.0.0.1:18081`
- 로그인 사용자 조회: `http://127.0.0.1:18081/api/auth/me`
- 기준정보 조회: `http://127.0.0.1:18081/api/equipment`

시연 계정의 공통 비밀번호는 **`LocalDemo!2026`**입니다.

| 계정 | 역할 |
|---|---|
| requester_a, requester_b | 요청자 |
| approver, approver_b | 승인자 |
| viewer | 조회자 |
| dual | 요청자 + 승인자 |
| inactive | 비활성 계정, 로그인 차단 확인용 |

요청자로 로그인하면 현재 장비를 조회하고 변경 사유를 입력해 임시 요청을 저장할 수 있습니다. 내 변경 요청 화면에서 목록과 상세를 확인하고 CSV를 업로드·검증할 수 있습니다. 승인자 계정의 실제 승인 처리는 아직 제공하지 않습니다.

CSV 흐름 확인 방법:

1. 요청자 계정으로 로그인하고 `내 변경 요청`에서 사유를 입력해 임시 저장합니다.
2. CSV 파일 선택에서 [오류 샘플](samples/catalog-invalid.csv)을 선택하고 `파일 업로드` → `검증하기`를 누릅니다.
3. 대여료 오류를 확인하고 [변경 샘플](samples/catalog-change.csv)로 교체해 다시 업로드·검증합니다.
4. 신규·변경·변경 없음과 장비별 전후 값을 확인합니다. 초기 장비 데이터 기준으로 각 1건입니다.
5. `원본 다운로드`로 저장된 바이트를 확인하고, 재접속 후 요청 상세에서 같은 검증 결과를 조회합니다.

기존 [신규 등록 샘플](samples/catalog-create.csv)도 사용할 수 있습니다. 샘플은 검증만으로 기준정보에 반영되지 않습니다.

프론트엔드 개발 서버 실행:

```powershell
cd frontend
npm run dev
```

백엔드가 `18081` 포트에서 실행 중이면 프론트엔드의 `/api` 요청을 백엔드로 전달합니다. 프론트엔드 주소는 `http://127.0.0.1:5174`입니다.

프론트엔드 타입 검사, 테스트, 빌드:

```powershell
npm run typecheck
npm run test:run
npm run build
```

실행 중인 로컬 서버에 연결하는 Chrome E2E 테스트(frontend 디렉터리 기준):

```powershell
$env:MCR_BASE_URL = 'http://127.0.0.1:18081'
npm run test:e2e
```

설치된 Chrome을 사용하며, E2E에서 생성한 검증용 임시 요청은 연결된 시연 DB에 남습니다.

프론트엔드를 빌드한 뒤 프로젝트 루트에서 `.\scripts\dev.ps1 -Tasks test,bootJar`를 실행하면 정적 화면이 포함된 JAR을 생성합니다. 일반 JAR 실행에는 별도로 준비된 DB 연결 설정이 필요하며, Embedded PostgreSQL은 운영 JAR에 포함되지 않습니다.

Eclipse에서는 Gradle Wrapper·Java 21·프로젝트별 Gradle user home `.gradle-user-home`을 사용합니다. 이 PC의 Gradle Arguments와 JVM Arguments에는 `-Dorg.gradle.internal.instrumentation.agent=false`를 지정합니다. 의존성 변경 후 F5 → Gradle → Refresh Gradle Project를 실행하고, 직접 실행할 때는 테스트 소스의 `LocalApplication`을 Java Application으로 실행합니다.

## 주요 API

| Method | URL | 설명 |
|---|---|---|
| `GET` | `/api/auth/csrf` | 변경 요청에 사용할 CSRF 토큰 조회 |
| `POST` | `/api/auth/login` | 아이디·비밀번호로 세션 로그인 |
| `POST` | `/api/auth/logout` | 세션 로그아웃 |
| `GET` | `/api/auth/me` | 로그인 사용자와 역할 조회 |
| `GET` | `/api/equipment` | 현재 장비 기준정보 조회 |
| `POST` | `/api/requests` | 본인 임시 요청 생성 |
| `GET` | `/api/requests/mine` | 본인 요청 목록 조회 |
| `GET` | `/api/requests/{requestId}` | 접근 정책에 따른 요청 상세 조회 |
| `GET` | `/api/requests/{requestId}/revisions/{revisionId}` | 접근 정책에 따른 리비전 상세 조회 |
| `POST` | `/api/requests/{requestId}/revisions/{revisionId}/file` | 현재 임시 리비전의 CSV 업로드·교체 |
| `GET` | `/api/requests/{requestId}/revisions/{revisionId}/file` | 권한에 따른 원본 파일 다운로드 |
| `POST` | `/api/requests/{requestId}/revisions/{revisionId}/validation` | 파일 ID를 대조해 검증·비교 결과 저장 |
| `GET` | `/api/requests/{requestId}/revisions/{revisionId}/workspace` | 파일·검증 결과와 기준정보 변경 여부 조회 |

로그인은 form 형식의 `username`·`password`, 임시 요청 생성은 JSON 형식의 `reason`을 받습니다. 파일 업로드는 multipart 형식의 `file`, 검증은 JSON 형식의 `fileId`를 받습니다. 모든 `POST` 요청은 CSRF 토큰이 필요합니다.

## 테스트에서 확인하는 내용

- CSV 인코딩·크기·레코드 수·헤더·필드와 파일 내 중복을 검증하는지
- 리비전 상태·역할·소유권을 조합한 도메인 정책이 허용되지 않은 접근을 거절하는지
- 세션 로그인·로그아웃, CSRF 검사와 세션 ID 갱신이 실제 HTTP에서 동작하는지
- 비활성 계정과 로그인 이후 역할 회수가 다음 API 요청에 반영되는지
- 타인의 임시 요청 조회와 작성자 ID 조작이 서버에서 차단되는지
- 앱 DB 계정이 계정 정보를 수정하거나 스키마를 생성하고 소유자 역할로 전환할 수 없는지
- 실제 PostgreSQL 종료·재시작 후 장비 값·버전·임시 요청·CSV 원본·검증 결과와 초기 계정 수가 유지되는지
- 타인·다른 요청 조합·과거 리비전·제출된 상태에서 파일 조회와 수정 권한이 구분되는지
- 파일 교체 시 이전 검증이 폐기되고 오래된 파일 ID로 검증할 수 없는지
- 검증 뒤 기존 장비가 바뀌거나 신규 코드가 생성되면 재검증이 필요한지
- 프론트엔드가 CSRF 토큰을 갱신하고 작성자 ID 없이 요청을 전송하는지
- 큰 정수 금액이 화면에서 정밀도 손실 없이 표시되는지
- 실제 Chrome에서 로그인·임시 저장·새로고침·계정 전환 후 본인 데이터만 표시되는지
- 조회자가 화면을 우회해 API를 직접 호출해도 임시 요청을 생성할 수 없는지

2026-10-15 기준 검증 결과:

| 구분 | 검증 결과 |
|---|---|
| CSV·권한·상태 단위 테스트 | 56건 통과 |
| 실제 PostgreSQL 연결 HTTP 인증·권한 테스트 | 20건 통과 |
| 실제 PostgreSQL 업로드·검증·비교 API 테스트 | 11건 통과 |
| 실제 PostgreSQL 종료·재시작 보존 테스트 | 1건 통과 |
| 프론트엔드 UI·CSRF 전송·bigint·CSV 비교 테스트 | 10건 통과 |
| 실제 서버에 연결한 Chrome E2E | 3건 통과 |

소유권 검사 제거 실험에서는 타인 상세 조회가 `200`이 되어 `403`을 기대한 대표 API 테스트가 실패했습니다. 원본 복원 후 전체 서버 테스트가 다시 통과했습니다. `SUBMITTED`·`APPLIED` 조회 권한 테스트는 DB에 준비한 상태를 사용하며, 실제 제출·승인 흐름을 검증한 결과는 아닙니다.

서버 테스트 보고서는 `build/reports/tests/test/index.html`, 브라우저 캡처는 `frontend/test-results/`에서 확인합니다. GitHub Actions 검증은 아직 구성하지 않았습니다.

## 개발 및 검증 방식

가상 장비 대여 조직을 기준으로 업무 규칙과 리비전·권한 정책을 정의했습니다. 구현 과정에서 AI 개발 도구를 보조적으로 활용했으며, 도메인 단위 테스트, 실제 PostgreSQL 기반 HTTP·권한·재시작 테스트와 프론트엔드 자동화 테스트로 동작을 확인했습니다.

도메인 정책 테스트와 실제 API·DB 검증의 범위를 구분해 기록합니다. 확정된 검증 결과와 다음 작업은 [인계 문서](docs/HANDOFF.md)에 정리했습니다.

## 현재 범위 밖의 항목

- 제출본 고정·내용 해시와 제출 API
- 승인과 DB 반영의 단일 트랜잭션 처리
- 반려·철회·새 리비전 생성과 처리 이력 화면
- 동시 승인·UNIQUE 경합·전체 반영 롤백과 응답 유실 후 재요청 검증
- GitHub Actions 백엔드·프론트엔드 검증
- OpenAPI 문서와 Swagger UI
- 회원 가입·계정 관리 화면과 운영 환경 배포 구성
- 컨테이너 런타임을 이용한 실행 검증

다음 단계에서는 유효한 검증 결과를 바탕으로 불변 제출본·내용 해시와 제출 처리를 연결합니다. 파일 상한은 성능 측정값이 아니며, 원본 파일 해시는 전자서명이나 승인 증명이 아닙니다.
