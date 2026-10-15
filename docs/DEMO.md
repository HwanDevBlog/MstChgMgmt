# MstChgMgmt 시연 안내

## 실행과 준비

프로젝트 루트에서 frontend의 `npm ci`, `npm run build`를 실행한 뒤
`.\scripts\dev.ps1 -Tasks localRun`으로 서버를 시작합니다.
웹 주소는 http://127.0.0.1:18081, DBeaver 설정과 계정 목록은 [README](../README.md)의 실행 방법을 따릅니다.
시연 계정 공통 비밀번호는 `LocalDemo!2026`입니다.

기존 DB는 보존됩니다. 이전 자동 검증의 요청과 장비가 보일 수 있으므로 요청 번호를 기록하고 진행합니다.
샘플의 CREATE 코드가 이미 존재하면 CSV 복사본에서 아직 없는 `EQ-` + 영문 대문자·숫자 6자리 코드로 바꿉니다.
초기 장비 값도 이미 변경됐다면 현재 기준정보에 맞게 UPDATE 값을 조정합니다. DB를 삭제해 초기화하지 않습니다.

## 제출부터 반영까지

1. `requester_a`로 로그인하고 기준정보 목록에서 현재 값과 버전을 확인합니다.
2. 내 변경 요청에서 사유를 입력하고 요청 임시 저장을 누릅니다.
3. 상세에서 `samples/catalog-invalid.csv`를 업로드·검증해 음수 금액 오류와 제출 차단을 확인합니다.
4. `samples/catalog-change.csv` 또는 준비한 복사본을 다시 업로드하고 검증합니다.
5. 신규·변경·변경 없음 행, 변경 전후 값과 사유를 확인합니다. 제출 내용 확인을 누르고 확인 체크 후 승인 요청 제출을 누릅니다.
6. 상태가 승인 대기로 바뀌고 제출본·내용 해시가 고정됐는지 확인합니다. 아직 기준정보 값은 바뀌지 않습니다.
7. 로그아웃하고 `approver`로 로그인합니다. 승인 대기에서 기록한 요청을 엽니다.
8. 고정 제출본과 사유를 확인하고 체크한 뒤 승인 및 반영을 누릅니다.
9. 반영 완료 상태와 실제 변경 이력을 확인합니다. 신규 장비 버전은 0, 변경 장비는 이전 버전+1이며 변경 없음 행은 버전을 유지합니다.
10. `viewer`로 로그인해 기준정보와 반영 이력을 조회합니다. 승인 기능과 원본 CSV 다운로드는 제공되지 않습니다.

## 반려·철회와 새 리비전

별도 요청을 제출한 뒤 `approver`가 사유를 입력하고 반려합니다.
`requester_a`가 같은 요청에서 새 리비전 작성을 누르면 원본 파일과 사유가 복사된 임시 리비전이 생성됩니다.
새 파일 식별자가 발급되고 이전 검증은 재사용하지 않으므로 다시 검증·검토·제출합니다.
리비전 이력에서 이전 반려 제출본과 처리 사유가 보존됐는지 확인합니다.

또 다른 승인 대기 요청에서 요청자가 요청 철회를 누릅니다. 철회 사유는 선택입니다.
철회 후에도 새 리비전을 작성할 수 있으며, 이전 제출본은 그대로 남습니다.
반영 완료된 요청은 새 리비전 작성 대상이 아닙니다.

## 기준정보 충돌 재현

1. 같은 장비를 서로 다른 값으로 UPDATE하는 요청 A와 B를 준비합니다. CREATE 행은 제외합니다.
2. 기준정보가 바뀌기 전에 두 요청 모두 검증하고 제출합니다.
3. 승인자가 A를 승인해 장비 값과 버전을 변경합니다.
4. B의 승인에서는 이전 값·기대 버전 불일치로 충돌이 안내되고 승인 대기 상태가 유지됩니다. B의 일부 행만 반영되지 않습니다.
5. 요청자가 B를 철회하고 새 리비전에서 현재 기준정보를 기준으로 다시 검증·제출합니다.

`dual`은 요청자와 승인자 역할을 함께 갖지만 본인 요청을 승인하거나 반려할 수 없습니다.
다른 요청자의 임시 요청은 접근할 수 없습니다. 화면 동작과 별도로 해당 API 권한은 자동 테스트로 검증합니다.

## DBeaver에서 결과 확인

아래 쿼리는 조회만 수행합니다. 비밀번호 해시나 원본 파일 바이트는 출력하지 않습니다.

```sql
-- 기준정보의 실제 값과 버전
SELECT code, name, category, daily_fee_krw, active, version
FROM catalog.equipment ORDER BY code;

-- 요청별 현재 리비전과 과거 상태
SELECT r.id AS request_id, r.current_revision_id,
       v.id AS revision_id, v.revision_number, v.state, v.based_on_revision_id
FROM catalog.change_request r
JOIN catalog.change_revision v ON v.request_id = r.id
ORDER BY r.id DESC, v.revision_number;

-- 제출본 해시와 처리 결과 대조
SELECT s.request_id, s.revision_id, s.content_hash AS submitted_hash,
       d.action, d.content_hash AS decided_hash, d.processed_at
FROM catalog.revision_submission s
LEFT JOIN catalog.revision_decision d ON d.revision_id = s.revision_id
ORDER BY s.request_id DESC, s.revision_id;

-- 실제 변경 행만 기록된다. 반려·철회는 장비 변경 행이 없다.
SELECT d.revision_id, d.action, c.equipment_code, c.operation,
       c.before_version, c.after_version, c.before_json, c.after_json
FROM catalog.revision_decision d
JOIN catalog.equipment_change c ON c.decision_id = d.decision_id
ORDER BY d.processed_at DESC, c.equipment_code;
```

## 검증 범위와 CI 확인

로컬 검증 근거는 서버 117건, 프론트엔드 17건, Chrome E2E 7건입니다.
행 갱신·최종 상태 갱신 실패의 전체 롤백, 동시 승인과 신규 코드 경합은 별도 임시 PostgreSQL 테스트로 확인했습니다.
시연 DB와 앱을 재시작한 뒤 기준정보·버전·제출본·처리 결과 보존도 확인했습니다.

GitHub Actions의 Backend CI와 Frontend CI는 설정 커밋을 main에 push한 뒤 Actions 탭에서 확인합니다.
2026-10-15 커밋 `cae4afa`에서 [Backend CI](https://github.com/HwanDevBlog/MstChgMgmt/actions/runs/38705508866)와 [Frontend CI](https://github.com/HwanDevBlog/MstChgMgmt/actions/runs/38705508783) 모두 성공한 것을 확인했습니다. Chrome E2E·시연 DB 재시작은 로컬 검증 결과입니다.
CI는 별도 임시 DB를 사용하며 이 PC의 시연 DB에 접속하지 않습니다.
