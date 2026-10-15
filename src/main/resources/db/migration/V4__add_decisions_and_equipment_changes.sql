ALTER TABLE change_revision ADD COLUMN based_on_revision_id bigint UNIQUE;
ALTER TABLE change_revision ADD CONSTRAINT fk_revision_base
    FOREIGN KEY (request_id,based_on_revision_id) REFERENCES change_revision(request_id,id);

CREATE TABLE revision_decision (
    decision_id uuid PRIMARY KEY,
    revision_id bigint NOT NULL UNIQUE REFERENCES change_revision(id),
    actor_id bigint NOT NULL REFERENCES app_user(id),
    action varchar(10) NOT NULL CHECK(action IN ('APPROVE','REJECT','WITHDRAW')),
    content_hash varchar(64) NOT NULL,
    reason varchar(500) NOT NULL,
    processed_at timestamptz NOT NULL,
    result_json text NOT NULL
);
CREATE TABLE equipment_change (
    id uuid PRIMARY KEY,
    decision_id uuid NOT NULL REFERENCES revision_decision(decision_id) DEFERRABLE INITIALLY DEFERRED,
    equipment_code varchar(9) NOT NULL,
    operation varchar(6) NOT NULL CHECK(operation IN ('CREATE','UPDATE')),
    before_json text,
    after_json text NOT NULL,
    before_version bigint,
    after_version bigint NOT NULL CHECK(after_version>=0),
    UNIQUE(decision_id,equipment_code)
);

CREATE FUNCTION reject_audit_mutation() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
    RAISE EXCEPTION 'Decision and change history are immutable' USING ERRCODE='55000';
END;
$$;
CREATE TRIGGER immutable_decision BEFORE UPDATE OR DELETE ON revision_decision
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
CREATE TRIGGER immutable_equipment_change BEFORE UPDATE OR DELETE ON equipment_change
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();

-- 승인 서비스에 필요한 쓰기 권한만 추가하고 계정·역할 변경 권한은 유지하지 않는다.
GRANT INSERT, UPDATE ON equipment TO mcr_app;
GRANT SELECT, INSERT ON revision_decision,equipment_change TO mcr_app;
