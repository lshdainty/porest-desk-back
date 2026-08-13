package com.porest.desk.dataimport.sms.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 문자의 카드 식별자 → 내 자산(카드) 연결 기억.
 *
 * <p>문자에는 "KB국민카드 1234" 까지만 있고 그게 내 어느 카드인지는 사용자만 안다.
 * 매번 물으면 쓸모가 없으므로, 한 번 고르면 여기 적어 두고 다음부터 자동으로 붙인다.
 *
 * <p>{@code cardHint} 는 {@code "카드사명|끝4자리"} 형태다({@code SmsParsed#cardHint}).
 * 끝자리를 못 읽는 마스킹 문자("1*3*")도 카드사만으로 기억할 수 있게 한쪽이 비어도 받는다.
 *
 * <p>(user, cardHint) 유니크라 재지정은 새 행이 아니라 기존 행의 자산 교체다.
 * 삭제 행도 되살려 쓴다({@link #relink}) — 지웠다 다시 만들면 유니크 제약에 걸린다.
 */
@Entity
@Table(name = "sms_card_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsCardMapping extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Column(name = "user_row_id", nullable = false)
    private Long userRowId;

    /** 카드 식별 힌트 — "KB국민카드|1234". 카드사나 끝자리 한쪽만 있을 수 있다. */
    @Column(name = "card_hint", nullable = false, length = 100)
    private String cardHint;

    @Column(name = "asset_row_id", nullable = false)
    private Long assetRowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static SmsCardMapping create(Long userRowId, String cardHint, Long assetRowId) {
        SmsCardMapping mapping = new SmsCardMapping();
        mapping.userRowId = userRowId;
        mapping.cardHint = cardHint;
        mapping.assetRowId = assetRowId;
        mapping.isDeleted = YNType.N;
        return mapping;
    }

    /** 연결 자산 교체 — 삭제 상태였으면 함께 되살린다. */
    public void relink(Long assetRowId) {
        this.assetRowId = assetRowId;
        this.isDeleted = YNType.N;
    }

    public void delete() {
        this.isDeleted = YNType.Y;
    }
}
