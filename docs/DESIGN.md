# MstChgMgmt — 장비 카탈로그 변경 검토 시스템 설계 v1

2026-10-14 규칙 확정, 2026-10-15 2단계 구현 완료. 단일 가상 장비 대여 조직의 카탈로그만 다룬다.
대여·반납·재고·주문·결제는 구현하지 않는다. 규칙과 예제는 이 프로젝트에서 독립적으로 정의했다.

## 1. 보장할 규칙과 현재 구현 범위

최종 대표 질문은 **승인 요청이 참조한 제출본과 실제 DB 반영 내용이 일치하는가?** 이다.
제출본 고정, 구 리비전 차단, 자기 승인 금지, 서버 권한 검사로 검증한다.
해시는 전자서명이나 사용자가 내용을 실제로 읽었다는 증거가 아니다.
계정 단위 직무 분리이며, 한 사람이 두 계정을 쓰는 것까지 차단하지 않는다.

현재 구현: CSV 검증·원본 해시·권한/상태 도메인, 세션 로그인·CSRF, PostgreSQL/Flyway, 기준정보 조회, 본인 DRAFT 생성/조회, React 화면.
현재 미구현: CSV 업로드·DB 존재 검증·전후 비교, 불변 제출본, 승인 트랜잭션과 변경 이력.
도메인 정책 테스트와 실제 PostgreSQL HTTP 권한 테스트를 구분한다. 자기 승인·구 리비전 정책의 승인 API 연결은 후속 단계다.

## 2. CSV 규칙 — catalog-v1

UTF-8만 허용하며 파일 맨 앞의 BOM 하나를 허용한다. 원본 해시는 BOM과 줄바꿈까지 포함한 원본 바이트로 계산한다.
최대 1,048,576바이트, 헤더 제외 데이터 레코드 200개. 이는 MVP 범위 제한이며 성능 측정값이 아니다.
LF·CRLF, CSV 따옴표로 감싼 쉼표와 이중 따옴표 이스케이프를 지원한다.
오류 위치는 헤더를 1로 하는 **논리 레코드 번호**다. 물리 줄 번호로 표시하지 않는다.
파일 오류는 레코드 0, 헤더 오류는 1로 표시한다. 문법 오류 위치는 다음 파싱 대상 레코드다.
빈 데이터 레코드를 건너뛰지 않는다. 마지막 줄바꿈 하나는 빈 레코드가 아니다.

고정 헤더와 순서:

```csv
operation,equipment_code,name,category,daily_fee_krw,active
```

| 필드 | 정확한 규칙 |
|---|---|
| operation | CREATE 또는 UPDATE. 존재 여부에 따른 자동 전환 없음 |
| equipment_code | `EQ-[A-Z0-9]{6}`. 생성 후 코드 변경 없음. 파일 내 모든 중복 발생 레코드 오류 |
| name | 공백만으로 구성 불가, 1~100 Unicode 코드 포인트. Java `strip()` 기준 앞뒤 공백 및 ISO 제어 문자 거부. 내부 일반 공백 허용 |
| category | CAMERA, AUDIO, LIGHTING, ACCESSORY 중 하나 |
| daily_fee_krw | `0` 또는 `[1-9][0-9]*`, 0~9,223,372,036,854,775,807. PostgreSQL bigint 대응. 부호·소수·지수·선행 0 거부 |
| active | 소문자 true 또는 false |

CREATE와 UPDATE 모두 전체 6개 필드를 요구한다. 공백 제거, 대소문자 변환, 기본값 보충을 하지 않는다.
이름은 Unicode 정규화하지 않는다. 데이터 레코드 0개는 오류다.
변경 사유는 공백만으로 구성 불가, 최대 500 Unicode 코드 포인트, NUL 불가. 사유를 자동으로 보충하지 않는다.
CSV 문법·UTF-8·용량·레코드 상한 오류에서는 부분 성공 데이터를 반환하지 않는다.
필드 오류는 정상 레코드와 함께 표시할 수 있으나 오류가 하나라도 있으면 제출 불가다.

후속 DB 검증: CREATE 대상이 존재하면 오류, UPDATE 대상이 없으면 오류.
미리보기는 신규/변경/변경 없음, 전후 값, 기대 버전을 보관한다. 변경 없음만 있는 요청은 제출 불가다.
파일 변경 시 검증·미리보기 결과를 폐기한다.
미리보기 이후 제출 시점에 기준정보가 바뀌면 재검토를 요구하며, 제출 시 기준을 조용히 바꾸지 않는다.

## 3. 권한표

모든 권한은 활성 계정에만 적용한다. 요청자·승인자 ID는 인증된 세션에서 취한다.
기준정보 목록과 APPLIED 상세·변경 이력은 세 역할 모두 조회 가능하도록 정한다.

| 동작 | REQUESTER | APPROVER | VIEWER |
|---|---|---|---|
| 요청 생성·내 목록 | 본인 | 불가 | 불가 |
| 미완료 상세·보존 리비전 조회 | 본인만 | 타인의 SUBMITTED만 | 불가 |
| CSV 원본 조회 | 본인만 | 타인의 SUBMITTED만 | 불가 |
| 파일·사유 수정·검증 | 본인 현재 DRAFT만 | 불가 | 불가 |
| 제출 | 본인 현재 DRAFT만, 유효 검증·변경 존재 필요 | 불가 | 불가 |
| 철회 | 본인 현재 SUBMITTED만 | 불가 | 불가 |
| 승인·반려 | 불가 | 타인 현재 SUBMITTED만 | 불가 |
| 새 DRAFT 리비전 생성 | 본인 현재 REJECTED/WITHDRAWN만 | 불가 | 불가 |
| APPLIED 상세·변경 이력 | 가능 | 가능 | 가능 |

복수 역할은 권한을 합치되 자기 승인·반려 금지가 우선한다.
APPLIED 이력 조회가 CSV 원본 접근까지 허용하지는 않는다.
소유자는 자기 과거 리비전을 읽을 수 있지만 과거 리비전을 수정·결정할 수 없다.
다른 요청자의 ID를 이용한 접근은 거부한다.
목록 쿼리와 상세·원본·변경 서비스에서 각각 권한 검사를 적용할 예정이다.
Spring Security URL 검사만으로 객체 소유권 검사를 대체하지 않는다.

## 4. 상태 전이

| 현재 | 허용 동작 | 결과 |
|---|---|---|
| DRAFT | 수정 | DRAFT |
| DRAFT | 제출 | SUBMITTED |
| SUBMITTED | 승인 및 DB 반영 | APPLIED |
| SUBMITTED | 반려 | REJECTED |
| SUBMITTED | 철회 | WITHDRAWN |
| REJECTED / WITHDRAWN | 새 리비전 생성 | 별도 DRAFT 행 생성, 기존 상태·내용 보존 |
| APPLIED | 읽기 | 종료, 잘못 반영한 경우 별도 요청 |

요청당 현재 리비전은 하나이며 모든 변경 동작에서 currentRevisionId를 대조한다.
SUBMITTED 이후 파일·사유·제출 내용은 수정 불가다.
승인 실패는 예외와 트랜잭션 롤백으로 SUBMITTED를 유지한다.
기준정보 충돌은 철회 후 새 리비전에서 재검토한다.
상태 정책은 허용 여부만 판단한다. 실제 상태 변경·잠금·현재 포인터 갱신은 후속 서비스·DB가 담당한다.
정책에서 SUBMIT 허용만으로 CSV 검증 완료나 변경 존재를 보장하지 않는다.

## 5. 후속 구현의 제출본 계약

승인 상세는 requestId, revisionId, contentHash, 사유, 행별 전후 값, 기대 버전을 한 묶음으로 반환한다.
화면이 렌더링한 그 묶음의 revisionId·hash만 결정 API로 보낸다. 백그라운드 조회로 식별자만 교체하지 않는다.
결정 API는 클라이언트 변경값을 받지 않고 서버 제출본에서만 반영값을 읽는다.

내용 해시 직렬화 v1은 다음의 **길이 접두사 바이너리**로 정한다. 구현과 고정 벡터 검증은 4단계다.

1. 정수는 big-endian. ID·버전·금액은 signed 64-bit, 길이·행 수는 signed 32-bit이며 음수 금지.
2. 문자열은 UTF-8 바이트 길이 32-bit + 원문 바이트. Unicode 정규화·trim 없음. 불리언은 00/01 한 바이트.
3. 순서: 문자열 `catalog-submission-v1`, requestId, revisionId, requesterId, rulesVersion, reason, 행 수.
4. 행은 equipment_code ASCII 오름차순. 각 행은 operation 문자열, equipment_code 문자열,
   before 존재 바이트, 존재 시 before(name, category, dailyFeeKrw, active),
   기대 버전 존재 바이트, 존재 시 버전, after(name, category, dailyFeeKrw, active) 순서.
5. CREATE의 before·기대 버전은 없음, UPDATE에는 둘 다 있음. 중복 코드는 사전 거부.
6. 변경 없음 행도 제출본에 포함한다. 값은 보존하며 해당 행에 실제 변경 이력이나 버전 증가를 만들지 않는다.
7. 전체 바이트의 SHA-256을 소문자 64자리 hex로 표현한다. CSV 원본 해시와 별도 필드로 저장한다.

## 6. 후속 DB·트랜잭션 계약

사용자·역할, 장비+증가 버전, 요청+현재 리비전, 리비전 상태, 불변 제출 내용,
CSV 원본+검증 결과, 처리·실제 변경 이력을 분리한다.
제출 내용은 앱 계정에서 UPDATE·DELETE 불가하도록 스키마 권한과 DB 트리거로 통제한다.
스키마 소유자와 앱 로그인 역할을 분리한다. DB 관리자까지 막는 위변조 방지라고 주장하지 않는다.

승인 단일 트랜잭션: 활성·권한 확인 → 요청 잠금 → 현재 리비전·상태·자기 승인 확인 →
서버 제출본 ID·hash 대조 → 대상 기존 장비를 코드 순으로 잠금 → 기대 버전 비교 →
CREATE 중복 확인+UNIQUE 제약 → 실제 변경·이력 저장 → APPLIED → 커밋.
변경 없음 행도 존재·버전 충돌 검사 대상이다. 사용자 검토 중에는 락을 보유하지 않는다.
신규 행은 잠글 대상이 없으므로 UNIQUE 제약이 최종 중복 방어다.
행 처리 중 실패하면 기준정보·성공 이력·승인 상태가 함께 롤백된다.
실패 진단은 추적 ID와 별도 앱 로그로 남기고 성공 이력으로 기록하지 않는다.

응답 유실 재요청은 먼저 활성·역할·자기 승인 금지를 검사하고, 동일 승인자·리비전·hash의
기존 성공 영수증이면 반환한다. 일반 APPROVE 상태 전이를 다시 실행하지 않는다.
다른 승인자의 중복 요청은 기존 승인자의 재시도와 구분하고 반영하지 않는다.

테스트 DB는 Docker 없이 실제 PostgreSQL을 구동하는 Embedded PostgreSQL을 검토한다.
시연 DB는 프로젝트 `.local` 아래 고정 데이터 디렉터리와 종료 시 보존 설정을 사용하고,
초기 데이터는 비어 있는 DB에만 넣도록 한다. 재시작 보존은 구현 후 실측한다.

## 7. 구현 순서와 검증 경계

1. 규칙·권한·상태 도메인과 단위 테스트 — 완료.
2. Spring Security 세션·CSRF, Flyway, DB 역할 분리, 초기 계정, 기준정보·내 요청 조회, React 로그인·조회 — 완료.
3. 업로드·원본 보존·DB 검증·전후 비교, 미리보기 이후 변경 감지.
4. 불변 제출본·내용 해시·철회·반려·새 리비전.
5. 승인 원자 반영·이력·버전 충돌·멱등 재시도.
6. 실제 PostgreSQL 통합, HTTP 권한, 동시성, 브라우저 E2E, 보호 로직 제거 실험, GitHub Actions, 시연.

DB 테스트와 정책 단위 테스트를 구분한다. 락·UNIQUE·롤백은 실제 PostgreSQL에서 검증한다.
E2E는 화면에 표시한 제출 묶음과 전송한 묶음 일치까지 확인한다.
원격 저장소 생성·push·공개·운영 배포는 범위 밖이다.

## 8. 근거와 구현 선택

Java 21, Spring Boot 3.5.16, Gradle 8 계열. 기본 도메인은 프레임워크에 의존하지 않는다.
CSV의 따옴표·쉼표 처리는 Apache Commons CSV 1.14.1을 사용한다. 문자열 split으로 CSV를 직접 파싱하지 않는다.
React·TypeScript·Spring Security·PostgreSQL·Flyway는 필요한 단계에서 의존성을 추가한다.
MyBatis·Spring Batch·범용 결재 엔진은 현재 필요한 문제가 없어 추가하지 않는다.

- [Spring Boot 지원 환경](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Commons CSV API](https://commons.apache.org/proper/commons-csv/apidocs/)
- [Embedded PostgreSQL](https://github.com/zonkyio/embedded-postgres)
- [Gradle Windows 캐시 이동 이슈](https://github.com/gradle/gradle/issues/31438)

## 9. 2단계 구현 계약 — 2026-10-15

Spring Security의 기본 세션 CSRF 저장소와 마스킹 처리기를 유지한다.
GET /api/auth/csrf에서 현재 마스킹 토큰과 헤더 이름을 반환하고 클라이언트는 변경 요청마다 다시 조회한다.
로그인 성공은 204, 잘못된 인증은 401, CSRF/권한 위반은 403이다.
로그인 시 세션 ID를 교체하며 이전 CSRF 토큰을 폐기한다. 세션 쿠키는 HttpOnly·SameSite=Lax다.
API마다 활성 상태와 역할을 DB에서 다시 읽고, URL 권한 검사 전에 인증 정보를 갱신한다.

| API | 적용 규칙 |
|---|---|
| GET /api/auth/csrf | 익명 허용, no-store, 세션 마스킹 CSRF 토큰 |
| POST /api/auth/login | CSRF 필요, form username/password |
| POST /api/auth/logout | CSRF 필요, 세션 무효화 |
| GET /api/auth/me | 활성 역할 계정만, ID·계정명·역할만 반환 |
| GET /api/equipment | 세 역할 조회, 코드 정렬, 금액·버전은 십진 문자열 |
| GET /api/requests/mine | 요청자만, 인증된 ID로 서버 쿼리 제한 |
| POST /api/requests | 요청자·CSRF 필요, reason만 허용, 요청자 ID는 인증 정보에서 취함 |
| GET /api/requests/{id} | 현재 리비전에 VIEW_DETAIL 소유권·상태 정책 적용 |
| GET /api/requests/{id}/revisions/{revisionId} | 요청/리비전 소속 대조 후 동일 정책 적용 |

DRAFT는 빈 사유를 허용하며, 사유가 있으면 최대 500 코드 포인트·NUL 불가다.
사유 필수·변경 존재·검증 완료 검사는 제출 단계에서 수행한다.
요청과 첫 DRAFT는 한 트랜잭션에서 생성한다. 현재 포인터는 요청 ID와 리비전 ID의 복합 FK로 소속을 검증한다.
다른 요청의 리비전 ID를 상세 URL에 넣으면 404다. 존재하는 타인 DRAFT 접근은 403이다.
알 수 없는 JSON 필드는 거부하며, 클라이언트 requesterId로 소유자를 지정할 수 없다.

DB V1: catalog.app_user, user_role, equipment, change_request, change_revision, 인덱스와 현재 리비전 복합 FK.
mcr_owner는 NOLOGIN 스키마 소유자, mcr_app은 제한된 로그인 역할이다.
별도 관리자 연결에서 SET ROLE mcr_owner 후 Flyway를 실행한다. 앱 자동 마이그레이션은 끄고 Hibernate validate만 사용한다.
앱은 계정·역할·장비 SELECT, 요청/리비전 SELECT·INSERT·UPDATE와 해당 시퀀스 USAGE·SELECT만 받는다.
계정 UPDATE·DDL·소유자 역할 전환 거부를 실제 앱 연결에서 검증했다.
Embedded PostgreSQL URL의 user 쿼리 파라미터가 앱 사용자 설정을 덮어쓰지 않도록 사용자 없는 JDBC URL을 구성한다.

테스트 DB는 임시 디렉터리, 시연 DB는 .local/postgres-data 고정 디렉터리다.
시연 PostgreSQL은 127.0.0.1:15433, 웹 서버는 127.0.0.1:18081에 바인딩한다.
시연 DB는 정리 옵션을 끄고 fsync=on으로 실행한다. 초기 데이터는 빈 DB에서만 한 번 생성한다.
종료·재시작 후 변경값·버전·요청과 계정 수 보존을 실제 PostgreSQL 테스트로 확인했다.
이는 전원 장애·장애 복구 실험이나 운영 DB 인증 강화의 증거가 아니다.

React·TypeScript·Vite 버전과 기본 폴더 구조는 InvMgmt의 호환 구성을 따른다.
화면에 보이는 역할별 메뉴는 편의이며 API 권한 검사로도 동일한 접근을 통제한다.
계정 전환·401 처리 시 이전 요청 목록과 상세를 비워 이전 계정 데이터가 남지 않도록 한다.
화면 전환 시 목록 조회가 시작되기 전부터 로딩 상태를 적용해 입력 폼 재생성으로 사유가 사라지는 문제를 막는다.
Chrome E2E는 입력 사유·전송 JSON·서버 반환 상세 일치, 계정 전환 시 요청 분리, 조회자의 직접 API 차단을 확인한다.

2단계 검증: 서버 77건(기존 단위 56+HTTP/DB 20+재시작 1), 프론트엔드 6건, Chrome E2E 2건 통과.
소유권 검사 제거 시 대표 API 테스트가 expected 403 / actual 200으로 실패했고 복원 후 전체 서버 검증을 통과했다.
제출·승인 처리와 불변 제출본 DB 통제는 아직 구현하지 않았다.
## 확정 화면 디자인

로그인은 미니멀 센터형, 업무 화면은 차분한 워크스페이스형으로 확정했다.
밝은 회녹색 배경·흰색 패널·녹색 강조색을 로그인, 기준정보 목록, 내 요청 작성·목록·상세에 적용했다.
기준정보의 변경 요청 작성 버튼은 요청자에게 표시하며 기존 본인 요청 화면으로 이동한다.
CSV 업로드·검증 비교·제출·승인·처리 결과는 확정 시안에 따라 후속 기능 구현 시 연결한다.
