package com.hwandevblog.mstchgmgmt.equipment;

import jakarta.persistence.*;

/**
 * 현재 장비 카탈로그 값과 증가하는 버전을 보관한다.
 * 변경 이력과 제출 시점의 값은 후속 단계의 별도 테이블에 저장한다.
 */
@Entity
@Table(name = "equipment")
public class Equipment {
    @Id
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String category;
    @Column(name = "daily_fee_krw", nullable = false)
    private long dailyFeeKrw;
    @Column(nullable = false)
    private boolean active;
    @Version
    @Column(nullable = false)
    private long version;

    protected Equipment() { }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public long getDailyFeeKrw() { return dailyFeeKrw; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
}
