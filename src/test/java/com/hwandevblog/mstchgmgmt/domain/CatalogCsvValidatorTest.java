package com.hwandevblog.mstchgmgmt.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogCsvValidatorTest {
    private static final String HEADER = String.join(",", CatalogRules.HEADER) + "\n";
    private final CatalogCsvValidator validator = new CatalogCsvValidator();

    private CatalogCsvValidator.Result validate(String csv) {
        return validator.validate(csv.getBytes(StandardCharsets.UTF_8));
    }
    private CatalogCsvValidator.Result one(String row) { return validate(HEADER + row + "\n"); }
    private void error(CatalogCsvValidator.Result result, String field, String code) {
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> {
            assertThat(e.field()).isEqualTo(field);
            assertThat(e.code()).isEqualTo(code);
        });
    }

    @Test void parsesFullCreateAndUpdateWithoutChangingValues() {
        var result = validate(HEADER + "CREATE,EQ-CAM001,여행 카메라,CAMERA,0,true\n"
                + "UPDATE,EQ-MIC001,Microphone,AUDIO,9223372036854775807,false\n");
        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().getFirst().name()).isEqualTo("여행 카메라");
        assertThat(result.rows().get(1).operation()).isEqualTo(CatalogCsvValidator.Operation.UPDATE);
        assertThat(result.rows().get(1).dailyFeeKrw()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.rows().get(1).active()).isFalse();
    }

    @Test void bomAndCrLfAreAcceptedButOriginalHashIsDistinct() {
        String csv = HEADER + "CREATE,EQ-CAM001,Camera,CAMERA,100,true\n";
        var plain = validate(csv);
        var bom = validate("\uFEFF" + csv.replace("\n", "\r\n"));
        assertThat(bom.valid()).isTrue();
        assertThat(bom.rows()).isEqualTo(plain.rows());
        assertThat(bom.originalSha256()).hasSize(64).isNotEqualTo(plain.originalSha256());
    }

    @Test void quotedCommasAndEscapedQuotesAreParsed() {
        var result = one("CREATE,EQ-CAM001,\"Camera, \"\"Travel\"\"\",CAMERA,100,true");
        assertThat(result.valid()).isTrue();
        assertThat(result.rows().getFirst().name()).isEqualTo("Camera, \"Travel\"");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "create,EQ-CAM001,Camera,CAMERA,100,true|operation|INVALID_OPERATION",
            "UPSERT,EQ-CAM001,Camera,CAMERA,100,true|operation|INVALID_OPERATION",
            "CREATE,eq-CAM001,Camera,CAMERA,100,true|equipment_code|INVALID_CODE",
            "CREATE,EQ-ABCDE,Camera,CAMERA,100,true|equipment_code|INVALID_CODE",
            "CREATE,EQ-ABCDEFG,Camera,CAMERA,100,true|equipment_code|INVALID_CODE",
            "CREATE,EQ-CAM001,,CAMERA,100,true|name|INVALID_NAME",
            "CREATE,EQ-CAM001,Camera,OTHER,100,true|category|INVALID_CATEGORY",
            "CREATE,EQ-CAM001,Camera,CAMERA,-1,true|daily_fee_krw|INVALID_FEE",
            "CREATE,EQ-CAM001,Camera,CAMERA,1.0,true|daily_fee_krw|INVALID_FEE",
            "CREATE,EQ-CAM001,Camera,CAMERA,1e3,true|daily_fee_krw|INVALID_FEE",
            "CREATE,EQ-CAM001,Camera,CAMERA,+1,true|daily_fee_krw|INVALID_FEE",
            "CREATE,EQ-CAM001,Camera,CAMERA,01,true|daily_fee_krw|INVALID_FEE",
            "CREATE,EQ-CAM001,Camera,CAMERA,9223372036854775808,true|daily_fee_krw|FEE_OUT_OF_RANGE",
            "CREATE,EQ-CAM001,Camera,CAMERA,100,TRUE|active|INVALID_ACTIVE",
            "UPDATE,EQ-CAM001,Camera,CAMERA,100,|active|INVALID_ACTIVE",
            "UPDATE,EQ-CAM001,Camera,CAMERA,,true|daily_fee_krw|INVALID_FEE"
    }, delimiter = '|')
    void reportsFieldErrors(String row, String field, String code) {
        var result = one(row);
        error(result, field, code);
        assertThat(result.errors()).allMatch(e -> e.recordNumber() == 2);
        assertThat(result.rows()).isEmpty();
    }

    @Test void duplicateInvalidatesBothOccurrencesEvenWithDifferentOperations() {
        var result = validate(HEADER + "CREATE,EQ-CAM001,Camera,CAMERA,100,true\n"
                + "UPDATE,EQ-CAM001,Camera,CAMERA,200,true\n");
        assertThat(result.errors()).containsExactly(
                new CatalogCsvValidator.Error(2, "equipment_code", "DUPLICATE_CODE"),
                new CatalogCsvValidator.Error(3, "equipment_code", "DUPLICATE_CODE"));
        assertThat(result.rows()).isEmpty();
    }

    @Test void doesNotSilentlyTrimNamesOrIgnoreBlankRecords() {
        error(one("CREATE,EQ-CAM001, Camera ,CAMERA,100,true"), "name", "INVALID_NAME");
        error(validate(HEADER + "\n"), "row", "COLUMN_COUNT");
    }

    @Test void enforcesNameCodePointLimitAndRejectsControlCharacters() {
        assertThat(one("CREATE,EQ-CAM001," + "가".repeat(100) + ",CAMERA,100,true").valid()).isTrue();
        error(one("CREATE,EQ-CAM001," + "가".repeat(101) + ",CAMERA,100,true"), "name", "INVALID_NAME");
        error(one("CREATE,EQ-CAM001,\"two\nlines\",CAMERA,100,true"), "name", "INVALID_NAME");
    }

    @ParameterizedTest @ValueSource(strings = {"", "operation,equipment_code", "active,name,operation"})
    void rejectsMissingOrDifferentHeader(String csv) { assertThat(validate(csv).valid()).isFalse(); }

    @Test void headerOnlyCannotBeSubmitted() { error(validate(HEADER), "file", "DATA_REQUIRED"); }

    @ParameterizedTest @ValueSource(strings = {
            "CREATE,EQ-CAM001,Camera,CAMERA,100",
            "CREATE,EQ-CAM001,Camera,CAMERA,100,true,extra"
    }) void checksExactColumnCount(String row) { error(one(row), "row", "COLUMN_COUNT"); }

    @Test void rejectsInvalidUtf8RatherThanReplacingCharacters() {
        error(validator.validate(new byte[] {(byte) 0xc3, 0x28}), "file", "INVALID_UTF8");
    }

    @Test void enforcesRowLimitIncludingInvalidRecords() {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i < 200; i++) csv.append("CREATE,EQ-%06d,Camera,CAMERA,0,true\n".formatted(i));
        assertThat(validate(csv.toString()).valid()).isTrue();
        error(validate(csv + "CREATE,EQ-000200,Camera,CAMERA,0,true\n"), "file", "TOO_MANY_ROWS");
    }

    @Test void enforcesByteLimitAtExactBoundary() {
        String prefix = HEADER + "CREATE,EQ-CAM001,\"";
        String suffix = "\",CAMERA,0,true\n";
        String csv = prefix + "a".repeat(CatalogRules.MAX_BYTES - prefix.length() - suffix.length()) + suffix;
        assertThat(validate(csv).errors()).noneMatch(e -> e.code().equals("FILE_TOO_LARGE"));
        error(validate(csv + "x"), "file", "FILE_TOO_LARGE");
    }

    @Test void malformedFileCannotExposePartialValidRows() {
        var result = validate(HEADER + "CREATE,EQ-CAM001,Camera,CAMERA,0,true\n"
                + "CREATE,EQ-MIC001,\"unterminated,AUDIO,0,true");
        error(result, "file", "MALFORMED_CSV");
        assertThat(result.rows()).isEmpty();
    }

    @Test void resultCollectionsAreImmutable() {
        var result = one("CREATE,EQ-CAM001,Camera,CAMERA,0,true");
        assertThatThrownBy(() -> result.rows().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void validatesReasonWithoutImplicitDefaults() {
        assertThat(CatalogRules.validReason(null)).isFalse();
        assertThat(CatalogRules.validReason("  ")).isFalse();
        assertThat(CatalogRules.validReason("a".repeat(501))).isFalse();
        assertThat(CatalogRules.validReason("reason\u0000")).isFalse();
        assertThat(CatalogRules.validReason("카탈로그 정비")).isTrue();
    }
}
