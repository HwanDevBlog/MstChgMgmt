-- 파일 교체와 검증 결과 폐기를 한 행의 원자적 갱신으로 처리한다.
CREATE TABLE revision_workspace (
    revision_id bigint PRIMARY KEY REFERENCES change_revision(id) ON DELETE CASCADE,
    file_id uuid NOT NULL,
    filename varchar(180) NOT NULL,
    original bytea NOT NULL CHECK (octet_length(original) <= 1048576),
    original_sha256 varchar(64) NOT NULL,
    validation_json text,
    uploaded_at timestamptz NOT NULL
);

GRANT SELECT, INSERT, UPDATE ON revision_workspace TO mcr_app;
