package com.hwandevblog.mstchgmgmt.domain;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * CSV 형식과 필드 값을 검증하고 원본 파일의 해시를 계산한다.
 * 기준정보 존재 여부와 기대 버전은 미리보기와 제출 단계에서 별도로 검사한다.
 */
public final class CatalogCsvValidator {
    public enum Operation { CREATE, UPDATE }
    public record Row(int recordNumber, Operation operation, String equipmentCode, String name,
                      String category, long dailyFeeKrw, boolean active) { }
    // 오류 위치는 헤더를 1로 하는 논리 레코드 번호로 표시해, 따옴표 안의 줄바꿈에도 기준을 유지한다.
    public record Error(int recordNumber, String field, String code) { }
    public record Result(String originalSha256, List<Row> rows, List<Error> errors) {
        public Result { rows = List.copyOf(rows); errors = List.copyOf(errors); }
        public boolean valid() { return errors.isEmpty(); }
    }

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setIgnoreEmptyLines(false).setTrim(false).setIgnoreSurroundingSpaces(false).get();

    public Result validate(byte[] original) {
        if (original == null) throw new IllegalArgumentException("Original bytes are required");
        String hash = sha256(original);
        if (original.length > CatalogRules.MAX_BYTES) return failure(hash, 0, "file", "FILE_TOO_LARGE");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(original)).toString();
        } catch (CharacterCodingException exception) {
            return failure(hash, 0, "file", "INVALID_UTF8");
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);

        List<Row> rows = new ArrayList<>();
        List<Error> errors = new ArrayList<>();
        Map<String, Integer> firstCodes = new HashMap<>();
        int recordNumber = 1;
        try (CSVParser parser = CSVParser.parse(text, FORMAT)) {
            var iterator = parser.iterator();
            if (!iterator.hasNext()) return failure(hash, 1, "header", "HEADER_REQUIRED");
            if (!iterator.next().toList().equals(CatalogRules.HEADER)) {
                return failure(hash, 1, "header", "HEADER_MISMATCH");
            }
            while (iterator.hasNext()) {
                CSVRecord record = iterator.next();
                recordNumber++;
                if (recordNumber > CatalogRules.MAX_ROWS + 1) {
                    return failure(hash, recordNumber, "file", "TOO_MANY_ROWS");
                }
                if (record.size() != CatalogRules.HEADER.size()) {
                    errors.add(new Error(recordNumber, "row", "COLUMN_COUNT"));
                    continue;
                }
                int errorsBefore = errors.size();
                String operation = record.get(0), code = record.get(1), name = record.get(2),
                        category = record.get(3), fee = record.get(4), active = record.get(5);
                if (!operation.equals("CREATE") && !operation.equals("UPDATE")) {
                    errors.add(new Error(recordNumber, "operation", "INVALID_OPERATION"));
                }
                if (!code.matches("EQ-[A-Z0-9]{6}")) {
                    errors.add(new Error(recordNumber, "equipment_code", "INVALID_CODE"));
                } else {
                    Integer first = firstCodes.putIfAbsent(code, recordNumber);
                    if (first != null) {
                        Error firstError = new Error(first, "equipment_code", "DUPLICATE_CODE");
                        if (!errors.contains(firstError)) errors.add(firstError);
                        errors.add(new Error(recordNumber, "equipment_code", "DUPLICATE_CODE"));
                    }
                }
                if (name.isBlank() || !name.equals(name.strip())
                        || name.codePointCount(0, name.length()) > CatalogRules.MAX_NAME_CODE_POINTS
                        || name.codePoints().anyMatch(Character::isISOControl)) {
                    errors.add(new Error(recordNumber, "name", "INVALID_NAME"));
                }
                if (!CatalogRules.CATEGORIES.contains(category)) {
                    errors.add(new Error(recordNumber, "category", "INVALID_CATEGORY"));
                }
                long feeValue = 0;
                // 부호, 소수, 선행 0을 임의로 해석하지 않도록 정수 표기를 제한한다.
                if (!fee.matches("0|[1-9][0-9]*")) {
                    errors.add(new Error(recordNumber, "daily_fee_krw", "INVALID_FEE"));
                } else {
                    try { feeValue = Long.parseLong(fee); }
                    catch (NumberFormatException exception) {
                        errors.add(new Error(recordNumber, "daily_fee_krw", "FEE_OUT_OF_RANGE"));
                    }
                }
                if (!active.equals("true") && !active.equals("false")) {
                    errors.add(new Error(recordNumber, "active", "INVALID_ACTIVE"));
                }
                if (errors.size() == errorsBefore) {
                    rows.add(new Row(recordNumber, Operation.valueOf(operation), code, name, category,
                            feeValue, Boolean.parseBoolean(active)));
                }
            }
            if (recordNumber == 1) errors.add(new Error(0, "file", "DATA_REQUIRED"));
        } catch (IOException | UncheckedIOException exception) {
            // CSV 문법 오류가 있으면 앞서 읽은 정상 행도 반환하지 않아 부분 검증 결과의 사용을 막는다.
            return failure(hash, recordNumber + 1, "file", "MALFORMED_CSV");
        }
        // 중복 코드가 뒤에서 발견된 경우에도 먼저 읽은 행을 포함해 모든 중복 행을 제외한다.
        rows.removeIf(row -> errors.stream().anyMatch(error -> error.recordNumber() == row.recordNumber()));
        return new Result(hash, rows, errors);
    }

    private static Result failure(String hash, int row, String field, String code) {
        return new Result(hash, List.of(), List.of(new Error(row, field, code)));
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
