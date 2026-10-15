# MstChgMgmt — 장비 카탈로그 기준정보 변경·검증·승인

Master Change Management의 축약명. 기존 InvMgmt와 같은 명명 방식으로 정했다.

가상 장비 대여업체의 카탈로그 변경을 검토하고 승인하는 토이 프로젝트.
현재 **1단계: CSV 규칙·권한·상태 정책**까지 구현했다. 웹 화면과 DB는 다음 단계다.

## 보장하려는 규칙

최종 목표는 승인 요청이 참조한 제출본과 실제 DB 반영 내용의 일치다.
제출본 고정, 구 리비전 승인 차단, 자기 승인 금지, 서버 권한 검사로 증명할 예정이다.

현재 코드로 검증하는 범위:

- UTF-8·BOM, 1MiB·200레코드 제한, 고정 헤더·6개 필드·파일 내 중복 검증.
- 원본 바이트 SHA-256. 승인 내용 해시는 별도 후속 구현이다.
- 활성 계정·역할·소유권·현재 리비전·상태를 조합한 도메인 정책.
- REQUESTER+APPROVER 계정도 자기 요청 승인·반려 불가.
- 제출 이후 수정 불가, 반려·철회 후 별도 리비전 생성, APPLIED 종료.

## 설계 판단

기존 InvMgmt와 같은 작업 공간·축약 프로젝트명·main 브랜치·com.hwandevblog 그룹을 사용한다.
Java 기본 패키지는 com.hwandevblog.mstchgmgmt이며, 커밋 메시지는 한국어로 변경 내용을 설명한다.
Java 21·Spring Boot·Gradle Wrapper·표준 소스/테스트 구조를 공통으로 사용한다.

독립적인 가상 업무 규칙은 [설계 문서](docs/DESIGN.md)에 정의했다.
CSV 따옴표·쉼표 처리는 Commons CSV로 하고, 업무값을 자동 보정하지 않는다.
권한 정책은 순수 Java로 분리해 먼저 검증한다. 서버 서비스와 연결하기 전에는 API 권한 보장을 주장하지 않는다.
새 리비전 생성은 기존 반려·철회 상태를 DRAFT로 바꾸는 동작과 구분한다.
검토 중 DB 락을 유지하지 않고, 승인과 실제 반영은 후속 단계에서 하나의 트랜잭션으로 묶는다.

## 재현 절차

Java 21 JDK가 필요하다. Docker Desktop, 전역 Gradle, PostgreSQL 설치는 현재 단위 테스트에 필요하지 않다.
첫 실행은 공식 Gradle 배포본과 Maven Central 의존성을 다운로드하므로 네트워크가 필요하다.

Windows PowerShell:

```powershell
cd C:\toyProject\workspace\MstChgMgmt
.\scripts\dev.ps1
.\scripts\dev.ps1 -Tasks test,bootJar
```

스크립트는 JAVA_HOME 또는 PATH의 javac를 사용하며, 없으면 이 PC의 Eclipse Java 21 번들을 찾는다.
다른 PC에서는 JAVA_HOME을 Java 21 JDK로 지정한다. 환경 변경은 현재 프로세스에 한정한다.
Gradle 캐시는 프로젝트 `.gradle-user-home`으로 격리한다(이미 지정한 GRADLE_USER_HOME은 존중).
이 Windows 환경에서 재현한 Gradle 변환 캐시 이동 오류는 스크립트의
`-Dorg.gradle.internal.instrumentation.agent=false`로 우회한다. 글로벌 설정은 바꾸지 않는다.

Eclipse에서도 Gradle 실행 설정을 별도로 맞춰야 한다. 가져오기 또는 프로젝트 Gradle 설정에서
프로젝트별 설정을 사용하고 Gradle Wrapper, Java 21, Gradle user home을 이 프로젝트의 `.gradle-user-home`으로 지정한다.
Gradle Arguments와 JVM Arguments에는 `-Dorg.gradle.internal.instrumentation.agent=false`를 지정한다.
이 PC의 로컬 `.settings/org.eclipse.buildship.core.prefs`에는 위 설정을 적용했다.
외부에서 설정 파일을 변경한 뒤에는 프로젝트 F5 → Gradle → Refresh Gradle Project를 실행한다.
`.settings`는 PC별 경로를 포함하므로 커밋하지 않는다.
Linux/macOS:

```sh
sh gradlew test bootJar
```

예제 파일: [samples/catalog-create.csv](samples/catalog-create.csv).
현재 업로드 화면·API는 없으므로, 예제는 다음 단계에서 사용할 고정 CSV 형식이다.

## 테스트·DB 결과

1단계 검증일: 2026-10-14. 상세 실행 결과와 다음 작업은 [인계 문서](docs/HANDOFF.md)에 기록한다.
단위 테스트 보고서: `build/reports/tests/test/index.html`.

| 구분 | 현재 상태 |
|---|---|
| CSV·권한·상태 단위 테스트 | 구현·실행 |
| 자기 승인·구 리비전 조건 제거 실험 | 대표 테스트 실패 여부 확인 |
| PostgreSQL·Flyway 스키마 | 미구현, DB 변경 없음 |
| HTTP 권한·CSRF 테스트 | 로그인·API 구현 이후 |
| 락·UNIQUE·전체 롤백·동시성·응답 유실 재요청 | 승인 트랜잭션 구현 이후 |
| 브라우저 E2E·GitHub Actions | 후속 단계 |

## 한계

현재는 실행 가능한 웹 MVP가 아니다. 도메인 정책 테스트를 실제 서버 권한·DB 보호의 증거로 확대하지 않는다.
파일 상한은 처리 성능 측정값이 아니다. 측정하지 않은 성능·안정성·운영 효과를 주장하지 않는다.
내용 해시는 전자서명이나 읽음 증명이 아니다. 실제 사람이 여러 계정을 쓰는 것은 통제 범위 밖이다.
외부 공개·원격 저장소·push·운영 배포는 진행하지 않았다.
