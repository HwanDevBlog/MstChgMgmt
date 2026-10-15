CREATE TABLE revision_submission (
    submission_id uuid PRIMARY KEY,
    request_id bigint NOT NULL,
    revision_id bigint NOT NULL UNIQUE,
    requester_id bigint NOT NULL REFERENCES app_user(id),
    file_id uuid NOT NULL,
    validation_id uuid NOT NULL,
    content_hash varchar(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    filename varchar(180) NOT NULL,
    original bytea NOT NULL CHECK (octet_length(original) <= 1048576),
    original_sha256 varchar(64) NOT NULL,
    snapshot_json text NOT NULL,
    submitted_at timestamptz NOT NULL,
    FOREIGN KEY (request_id, revision_id) REFERENCES change_revision(request_id, id)
);

-- 앱 권한과 트리거를 함께 적용해 제출본의 수정·삭제를 차단한다.
CREATE FUNCTION reject_submission_mutation() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
    RAISE EXCEPTION 'Submitted content is immutable' USING ERRCODE='55000';
END;
$$;
CREATE TRIGGER immutable_submission BEFORE UPDATE OR DELETE ON revision_submission
    FOR EACH ROW EXECUTE FUNCTION reject_submission_mutation();

GRANT SELECT, INSERT ON revision_submission TO mcr_app;
