package com.porest.desk.asset.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;

/**
 * 이체의 <b>숫자 규칙</b> — 자산을 조회하기 전에 값만 보고 끊을 수 있는 것들.
 *
 * <p>이체를 만드는 길과 이체를 <b>예약</b>하는 길(반복·프리셋)이 같은 규칙을 봐야 한다.
 * 예약 쪽이 이걸 안 보면 잘못된 값이 "저장됐다" 고 남았다가 자정 배치에서만 조용히 실패한다.
 * 그렇다고 예약 쪽에 같은 조건을 다시 적으면 한쪽만 고쳐져 갈라진다 — 그래서 여기 한 벌만 둔다.
 *
 * <p>반대로 <b>자산 종류</b>에 걸린 규칙은 여기 없다. 그쪽은 부르는 자리마다 답이 달라서
 * ({@code AssetServiceImpl.validateTransfer} 는 신용카드를 허용해야 하고, 반복은 막아야 한다)
 * 각자 자기 자리에서 본다.
 */
public final class AssetTransferRules {

    private AssetTransferRules() {
    }

    /**
     * 값만으로 판단되는 이체 규칙을 검사하고, 정규화한 이자를 돌려준다.
     *
     * @return {@code interestAmount} 를 null 이면 0 으로 맞춘 값
     */
    public static long validateAmounts(Long fromAssetRowId, Long toAssetRowId,
                                       Long amount, Long fee, Long interestAmount) {
        // 양쪽 자산이 없으면 조회로 가기 전에 끊는다. findAssetOrThrow 는 null 을 그대로
        // QueryDSL 에 넘기는데 eq(null) 은 IllegalArgumentException 이고, @Repository 프록시가
        // 그걸 InvalidDataAccessApiUsageException 으로 번역해 매핑이 없는 채로 500 이 됐다
        // (QA 2026-09-07 #85). DTO 에도 @NotNull 이 있지만 이 자리는 카드 결제·매수 충당 같은
        // 서버 안쪽 호출도 지난다 — 그쪽엔 @Valid 가 닿지 않는다.
        if (fromAssetRowId == null || toAssetRowId == null) {
            throw new InvalidValueException(DeskErrorCode.REQUIRED_VALUE_MISSING);
        }
        // 같은 자산으로의 이체는 무의미·잘못된 잔액 이력 유발 — 차단.
        if (fromAssetRowId.equals(toAssetRowId)) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRANSFER_SAME_ASSET);
        }
        // 이체 금액은 0보다 커야 함 — 음수는 잔액 흐름을 역전시켜 자금이 거꾸로 이동한다.
        if (amount == null || amount <= 0) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRANSFER_INVALID_AMOUNT);
        }
        // 수수료도 음수면 안 된다. 출금은 -(amount + fee) 라 fee 가 음수면 그만큼 덜 빠지고
        // 입금은 그대로 들어와 없던 돈이 생긴다(100,000 이체에 fee -50,000 → 순자산 +50,000).
        if (fee != null && fee < 0) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRANSFER_INVALID_AMOUNT);
        }
        // 이자는 상환액 안에 포함된 몫이라 그보다 클 수 없다. 같으면 원금이 0 이라 부채가
        // 전혀 안 줄어드는데, 이자만 내는 거치 상환에서 실제로 있는 일이다.
        long interest = interestAmount != null ? interestAmount : 0L;
        if (interest < 0 || interest > amount) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRANSFER_INVALID_INTEREST);
        }
        return interest;
    }
}
