package com.porest.desk.expense.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.service.AssetTransferRules;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.type.TxKind;

/**
 * 반복 거래·프리셋이 <b>이체</b>를 가리킬 때의 검증.
 *
 * <p>숫자 규칙(금액·수수료·이자 범위·같은 자산)은 {@link AssetTransferRules} 한 벌을 쓴다.
 * 다만 그 한 벌은 두 묶음이고, <b>예약은 "금액" 묶음을 금액이 있을 때만</b> 본다 —
 * 프리셋은 금액을 비워 두는 것이 정상 용도이기 때문이다. 여기 적는 건 그 분기와
 * <b>예약이라서 달라지는 것</b>뿐이다.
 *
 * <ul>
 *   <li><b>카드 금지.</b> 공용 이체 검증({@code AssetServiceImpl.validateTransfer})은 체크카드만
 *       막고 신용카드는 <b>일부러 허용</b>한다 — 카드 자동결제가 결제 계좌에서 신용카드로 들어가는
 *       이체이기 때문이다. 거기에 "카드 금지" 를 넣으면 매달 결제일에 죽는다. 반복·프리셋에서는
 *       그 자리를 카드 설정의 자동 결제가 이미 맡고 있어 또 걸면 이중으로 빠진다.</li>
 *   <li><b>이자는 받는 자산이 대출일 때만.</b> 공용 쪽은 범위만 본다(입력 화면이 대출일 때만
 *       칸을 띄우므로). 예약은 만든 뒤 계좌를 바꿀 수 있어 저장 때 한 번 더 본다.</li>
 *   <li><b>카테고리 없음.</b> 이체는 자산 간 이동이라 지출/수입 분류에 들어가지 않는다.</li>
 * </ul>
 *
 * <p>검사 시점은 <b>저장할 때</b>다. 자정 배치는 실패해도 로그만 남으므로, 만들 때 안 막으면
 * 사용자는 "저장됐다" 고 믿고 매일 밤 조용히 실패하는 규칙을 갖게 된다.
 */
public final class RecurringTransferValidator {

    private RecurringTransferValidator() {
    }

    /**
     * @param kind     만들 거래 종류
     * @param category 고른 카테고리 (이체면 없어야 한다)
     * @param from     보내는 자산
     * @param to       받는 자산 (이체가 아니면 null 이어야 한다)
     * @param amount   금액
     * @param fee      수수료
     * @param interest 이자 — 받는 자산이 대출일 때만 쓴다
     */
    public static void validate(TxKind kind, ExpenseCategory category,
                                Asset from, Asset to,
                                Long amount, Long fee, Long interest) {
        if (kind != TxKind.TRANSFER) {
            // 지출·수입인데 이체 칸이 채워져 있으면 저장하지 않는다. 그대로 두면 나중에 종류만
            // 바꿨을 때 쓰레기 값이 살아난다.
            if (to != null || fee != null || interest != null) {
                throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
            }
            return;
        }

        if (category != null) {
            throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
        }
        if (from == null || to == null) {
            throw new InvalidValueException(DeskErrorCode.REQUIRED_VALUE_MISSING);
        }
        AssetTransferRules.validateParties(from.getRowId(), to.getRowId());
        // 금액은 <b>있을 때만</b> 본다. 프리셋은 금액을 비워 두는 것이 정상 용도다 — 대출
        // 이자처럼 매달 금액만 다른 이체는 계좌·수수료만 적어 두고, 불러올 때 금액을 채운다
        // (`ExpenseTemplateServiceImpl.resolveAmount` 가 고정을 끄면 null 을 넘긴다).
        // 반복은 금액이 없을 수 없다 — `RecurringTransactionServiceImpl.validateAmount` 가
        // 앞서 막는다. 그러니 이 분기는 사실상 프리셋만 탄다.
        if (amount != null) {
            AssetTransferRules.validateMoney(amount, fee, interest);
        } else {
            AssetTransferRules.validateMoneySigns(fee, interest);
        }

        if (isCard(from) || isCard(to)) {
            throw new InvalidValueException(DeskErrorCode.RECURRING_TRANSFER_CARD_NOT_ALLOWED);
        }
        if (interest != null && interest != 0L && to.getAssetType() != AssetType.LOAN) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRANSFER_INVALID_INTEREST);
        }
    }

    private static boolean isCard(Asset a) {
        return a.getAssetType() == AssetType.CHECK_CARD
            || a.getAssetType() == AssetType.CREDIT_CARD;
    }
}
