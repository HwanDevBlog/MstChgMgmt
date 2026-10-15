package com.hwandevblog.mstchgmgmt.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwandevblog.mstchgmgmt.domain.CatalogCsvValidator;
import com.hwandevblog.mstchgmgmt.domain.CatalogRules;
import com.hwandevblog.mstchgmgmt.domain.RevisionPolicy;
import com.hwandevblog.mstchgmgmt.equipment.Equipment;
import com.hwandevblog.mstchgmgmt.equipment.EquipmentRepository;
import com.hwandevblog.mstchgmgmt.security.SessionUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 현재 임시 리비전의 CSV 원본과 검증 시점 기준정보를 보관한다.
 * 파일 교체는 이전 검증을 폐기하고 조회 시에는 기준정보 변경 여부를 다시 확인한다.
 */
@Service
@Transactional(readOnly = true)
public class RevisionWorkspaceService {
    private final ChangeRequestRepository requests;
    private final ChangeRevisionRepository revisions;
    private final EquipmentRepository equipment;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RevisionPolicy policy = new RevisionPolicy();
    private final CatalogCsvValidator validator = new CatalogCsvValidator();

    public RevisionWorkspaceService(ChangeRequestRepository requests, ChangeRevisionRepository revisions,
                                    EquipmentRepository equipment, JdbcTemplate jdbc, ObjectMapper json) {
        this.requests = requests; this.revisions = revisions; this.equipment = equipment;
        this.jdbc = jdbc; this.json = json;
    }

    public record Values(String name, String category, String dailyFeeKrw, boolean active, String version) { }
    public record Comparison(int recordNumber, String operation, String equipmentCode, String kind,
                             Values before, Values after, String expectedVersion) { }
    public record Preview(UUID validationId, UUID fileId, String rulesVersion, boolean valid, boolean stale,
                          int created, int changed, int unchanged, List<Comparison> rows,
                          List<CatalogCsvValidator.Error> errors, Instant validatedAt) {
        Preview withStale(boolean value) {
            return new Preview(validationId, fileId, rulesVersion, valid, value, created, changed, unchanged, rows, errors, validatedAt);
        }
    }
    public record Workspace(UUID fileId, String filename, int size, String originalSha256, Preview preview) { }
    public record Original(String filename, byte[] bytes) { }
    private record Stored(UUID fileId, String filename, byte[] bytes, String sha256, String validation) { }

    public Workspace get(SessionUser user, long requestId, long revisionId) {
        authorize(user, requestId, revisionId, RevisionPolicy.Action.VIEW_ORIGINAL, false);
        var stored = stored(revisionId);
        if (stored == null) return new Workspace(null, null, 0, null, null);
        Preview preview = stored.validation() == null ? null : decode(stored.validation());
        if (preview != null) preview = preview.withStale(isStale(preview));
        return workspace(stored, preview);
    }

    public Original original(SessionUser user, long requestId, long revisionId) {
        authorize(user, requestId, revisionId, RevisionPolicy.Action.VIEW_ORIGINAL, false);
        var frozen = jdbc.query("SELECT filename,original FROM revision_submission WHERE revision_id=?",
                (rs,n) -> new Original(rs.getString(1),rs.getBytes(2)),revisionId);
        if (!frozen.isEmpty()) return frozen.getFirst();
        var stored = required(revisionId);
        return new Original(stored.filename(), stored.bytes());
    }

    @Transactional
    public Workspace upload(SessionUser user, long requestId, long revisionId, MultipartFile file) throws IOException {
        authorize(user, requestId, revisionId, RevisionPolicy.Action.EDIT, true);
        // 파일이 신고한 크기만 신뢰하지 않고 읽어 들이는 바이트 수도 제한한다.
        if (file.getSize() > CatalogRules.MAX_BYTES) throw tooLarge();
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(CatalogRules.MAX_BYTES + 1); }
        if (bytes.length > CatalogRules.MAX_BYTES) throw tooLarge();
        String filename = safeFilename(file.getOriginalFilename());
        UUID fileId = UUID.randomUUID();
        String sha256 = validator.validate(bytes).originalSha256();
        jdbc.update("""
                INSERT INTO revision_workspace(revision_id,file_id,filename,original,original_sha256,uploaded_at)
                VALUES (?,?,?,?,?,now()) ON CONFLICT (revision_id) DO UPDATE SET
                file_id=EXCLUDED.file_id,filename=EXCLUDED.filename,original=EXCLUDED.original,
                original_sha256=EXCLUDED.original_sha256,uploaded_at=EXCLUDED.uploaded_at,validation_json=NULL
                """, revisionId, fileId, filename, bytes, sha256);
        return new Workspace(fileId, filename, bytes.length, sha256, null);
    }

    @Transactional
    public Preview validate(SessionUser user, long requestId, long revisionId, UUID fileId) {
        authorize(user, requestId, revisionId, RevisionPolicy.Action.EDIT, true);
        var stored = required(revisionId);
        // 다른 화면에서 교체한 파일을 현재 화면의 파일처럼 검증하지 않는다.
        if (!stored.fileId().equals(fileId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "STALE_FILE");
        var parsed = validator.validate(stored.bytes());
        var errors = new ArrayList<>(parsed.errors());
        var current = equipment.findAllById(parsed.rows().stream().map(CatalogCsvValidator.Row::equipmentCode).toList())
                .stream().collect(Collectors.toMap(Equipment::getCode, Function.identity()));
        var rows = new ArrayList<Comparison>();
        for (var row : parsed.rows()) {
            var previous = current.get(row.equipmentCode());
            if (row.operation() == CatalogCsvValidator.Operation.CREATE && previous != null) {
                errors.add(new CatalogCsvValidator.Error(row.recordNumber(), "equipment_code", "CREATE_EXISTS"));
                continue;
            }
            if (row.operation() == CatalogCsvValidator.Operation.UPDATE && previous == null) {
                errors.add(new CatalogCsvValidator.Error(row.recordNumber(), "equipment_code", "UPDATE_MISSING"));
                continue;
            }
            Values before = previous == null ? null : values(previous);
            // 신규 값의 버전은 아직 생성하지 않으며 기존 행은 검증 시점의 기대 버전을 보관한다.
            Values after = new Values(row.name(), row.category(), Long.toString(row.dailyFeeKrw()), row.active(),
                    before == null ? null : before.version());
            String kind = before == null ? "CREATE" : before.equals(after) ? "UNCHANGED" : "CHANGED";
            rows.add(new Comparison(row.recordNumber(), row.operation().name(), row.equipmentCode(), kind,
                    before, after, before == null ? null : before.version()));
        }
        var preview = new Preview(UUID.randomUUID(), fileId, CatalogRules.VERSION, errors.isEmpty(), false,
                count(rows, "CREATE"), count(rows, "CHANGED"), count(rows, "UNCHANGED"),
                List.copyOf(rows), List.copyOf(errors), Instant.now());
        jdbc.update("UPDATE revision_workspace SET validation_json=? WHERE revision_id=?", encode(preview), revisionId);
        return preview;
    }

    private boolean isStale(Preview preview) {
        var current = equipment.findAllById(preview.rows().stream().map(Comparison::equipmentCode).toList())
                .stream().collect(Collectors.toMap(Equipment::getCode, Function.identity()));
        return preview.rows().stream().anyMatch(row -> {
            var latest = current.get(row.equipmentCode());
            return !Objects.equals(row.before(), latest == null ? null : values(latest));
        });
    }

    private void authorize(SessionUser user, long requestId, long revisionId, RevisionPolicy.Action action, boolean lock) {
        var revision = (lock ? revisions.findLockedById(revisionId) : revisions.findById(revisionId))
                .filter(row -> row.getRequestId().equals(requestId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var request = requests.findById(requestId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        policy.require(user.actor(), new RevisionPolicy.Revision(request.getRequesterId(), revision.getId(),
                request.getCurrentRevisionId(), revision.getState()), action);
    }

    private Stored stored(long revisionId) {
        var rows = jdbc.query("SELECT file_id,filename,original,original_sha256,validation_json FROM revision_workspace WHERE revision_id=?",
                (rs, number) -> new Stored(rs.getObject(1, UUID.class), rs.getString(2), rs.getBytes(3), rs.getString(4), rs.getString(5)), revisionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private Stored required(long revisionId) {
        var value = stored(revisionId);
        if (value == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "FILE_REQUIRED");
        return value;
    }
    private Workspace workspace(Stored stored, Preview preview) {
        return new Workspace(stored.fileId(), stored.filename(), stored.bytes().length, stored.sha256(), preview);
    }
    private Values values(Equipment row) {
        return new Values(row.getName(), row.getCategory(), Long.toString(row.getDailyFeeKrw()), row.isActive(), Long.toString(row.getVersion()));
    }
    private int count(List<Comparison> rows, String kind) { return (int) rows.stream().filter(row -> row.kind().equals(kind)).count(); }
    private String safeFilename(String value) {
        String name = value == null ? "catalog.csv" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank()) return "catalog.csv";
        return name.codePointCount(0, name.length()) > 180 ? name.substring(0, name.offsetByCodePoints(0, 180)) : name;
    }
    private ResponseStatusException tooLarge() { return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE"); }
    private String encode(Preview preview) {
        try { return json.writeValueAsString(preview); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }
    private Preview decode(String value) {
        try { return json.readValue(value, Preview.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }
}
