package com.hwandevblog.mstchgmgmt.domain;

import java.util.List;
import java.util.Set;

/**
 * 장비 카탈로그의 CSV 양식과 필드별 검증 기준을 정의한다.
 * 파일 크기와 행 수 제한, 변경 사유 규칙을 검증기와 서비스에서 공통으로 사용한다.
 */
public final class CatalogRules {
    public static final String VERSION = "catalog-v1";
    public static final int MAX_BYTES = 1_048_576;
    public static final int MAX_ROWS = 200;
    public static final int MAX_NAME_CODE_POINTS = 100;
    public static final int MAX_REASON_CODE_POINTS = 500;
    public static final Set<String> CATEGORIES = Set.of("CAMERA", "AUDIO", "LIGHTING", "ACCESSORY");
    public static final List<String> HEADER = List.of(
            "operation", "equipment_code", "name", "category", "daily_fee_krw", "active");

    private CatalogRules() { }

    public static boolean validReason(String reason) {
        return reason != null && !reason.isBlank()
                && reason.codePointCount(0, reason.length()) <= MAX_REASON_CODE_POINTS
                && reason.codePoints().noneMatch(cp -> cp == 0);
    }
}
