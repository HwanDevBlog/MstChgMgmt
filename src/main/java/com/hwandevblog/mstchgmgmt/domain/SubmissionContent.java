package com.hwandevblog.mstchgmgmt.domain;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 검토한 변경 내용을 정해진 길이 접두사 바이너리 형식으로 직렬화한다.
 * 원본 CSV 해시와 구분되는 제출 내용 해시를 계산한다.
 */
public record SubmissionContent(long requestId, long revisionId, long requesterId,
                                String rulesVersion, String reason, List<Item> rows) {
    public record Value(String name, String category, long dailyFeeKrw, boolean active) {
        public Value { Objects.requireNonNull(name); Objects.requireNonNull(category); nonnegative(dailyFeeKrw); }
    }
    public record Item(String operation, String equipmentCode, Value before, Long expectedVersion, Value after) {
        public Item {
            Objects.requireNonNull(equipmentCode); Objects.requireNonNull(after);
            if (!equipmentCode.matches("EQ-[A-Z0-9]{6}")) throw new IllegalArgumentException("Invalid code");
            if ("CREATE".equals(operation)) {
                if (before != null || expectedVersion != null) throw new IllegalArgumentException("CREATE must not have a baseline");
            } else if ("UPDATE".equals(operation)) {
                Objects.requireNonNull(before); Objects.requireNonNull(expectedVersion); nonnegative(expectedVersion);
            } else throw new IllegalArgumentException("Invalid operation");
        }
    }
    public SubmissionContent {
        nonnegative(requestId); nonnegative(revisionId); nonnegative(requesterId);
        Objects.requireNonNull(rulesVersion); Objects.requireNonNull(reason);
        rows = rows.stream().sorted(Comparator.comparing(Item::equipmentCode)).toList();
        if (rows.stream().map(Item::equipmentCode).distinct().count() != rows.size()) throw new IllegalArgumentException("Duplicate code");
    }
    public String hash() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes()));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    public byte[] bytes() {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            string(out, "catalog-submission-v1");
            out.writeLong(requestId); out.writeLong(revisionId); out.writeLong(requesterId);
            string(out, rulesVersion); string(out, reason); out.writeInt(rows.size());
            for (var row : rows) {
                string(out, row.operation()); string(out, row.equipmentCode());
                out.writeBoolean(row.before() != null);
                if (row.before() != null) value(out, row.before());
                out.writeBoolean(row.expectedVersion() != null);
                if (row.expectedVersion() != null) out.writeLong(row.expectedVersion());
                value(out, row.after());
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }
    private static void string(DataOutputStream out, String value) throws IOException {
        // UTF-8 바이트 길이를 사용하고 trim이나 Unicode 정규화를 하지 않는다.
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static void value(DataOutputStream out, Value value) throws IOException {
        string(out, value.name()); string(out, value.category()); out.writeLong(value.dailyFeeKrw()); out.writeBoolean(value.active());
    }
    private static void nonnegative(long value) { if (value < 0) throw new IllegalArgumentException("Negative number"); }
}
