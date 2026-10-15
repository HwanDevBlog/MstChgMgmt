package com.hwandevblog.mstchgmgmt.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * 독립적으로 계산한 고정 벡터로 내용 해시의 직렬화 계약을 검증한다.
 * 행 순서와 필드·사유·버전 변경이 해시에 미치는 영향을 구분한다.
 */
class SubmissionContentTest {
    private SubmissionContent.Value camera(long fee) { return new SubmissionContent.Value("여행용 카메라","CAMERA",fee,true); }
    private List<SubmissionContent.Item> items() {
        var light=new SubmissionContent.Value("조명","LIGHTING",5000,false);
        return List.of(new SubmissionContent.Item("UPDATE","EQ-CAM100",camera(15000),7L,camera(17000)),
                new SubmissionContent.Item("UPDATE","EQ-LGT100",light,0L,light),
                new SubmissionContent.Item("CREATE","EQ-NEW001",null,null,new SubmissionContent.Value("새 장비","ACCESSORY",Long.MAX_VALUE,false)));
    }
    private SubmissionContent content(String reason,List<SubmissionContent.Item> rows) { return new SubmissionContent(1,2,3,"catalog-v1",reason,rows); }
    @Test void matchesIndependentBigEndianUtf8Vector() {
        var content=content("요금 변경\n검토",items());
        assertThat(content.bytes()).hasSize(364);
        assertThat(content.hash()).isEqualTo("16e894d120581b639ea00bb574c3537df77656068acd20e27fa6b4a4664b0f7d");
    }
    @Test void sortsRowsWithoutChangingTheHash() {
        var rows=items();
        assertThat(content("사유",List.of(rows.get(2),rows.get(0),rows.get(1))).hash()).isEqualTo(content("사유",rows).hash());
    }
    @Test void reasonAndExpectedVersionAndUnchangedRowsArePartOfTheContent() {
        var rows=items(); var original=content("사유",rows).hash();
        assertThat(content("사유 ",rows).hash()).isNotEqualTo(original);
        var changed=new SubmissionContent.Item("UPDATE","EQ-CAM100",camera(15000),8L,camera(17000));
        assertThat(content("사유",List.of(changed,rows.get(1),rows.get(2))).hash()).isNotEqualTo(original);
        assertThat(content("사유",List.of(rows.get(0),rows.get(2))).hash()).isNotEqualTo(original);
        assertThat(content("가",rows).hash()).isNotEqualTo(content("가",rows).hash());
    }
    @Test void rejectsDuplicateCodesAndInvalidBaselines() {
        assertThatThrownBy(()->content("사유",List.of(items().get(0),items().get(0)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SubmissionContent.Item("CREATE","EQ-CAM100",camera(1),0L,camera(2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SubmissionContent.Item("UPDATE","EQ-CAM100",camera(1),-1L,camera(2))).isInstanceOf(IllegalArgumentException.class);
    }
}
