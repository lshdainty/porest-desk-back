package com.porest.desk.asset.type;

/**
 * 잔액 부호 규약 — <b>부호는 사용자가 아니라 자산 종류가 정한다</b>(QA 2026-09-03 #17 #19 #21).
 *
 * <p>사용자는 화면에서 잔액·사용액을 늘 <b>절대값</b>으로 친다("현재 사용액", "잔액").
 * 그 값에 부호를 씌우는 규칙과, 집계에서 자산군·부채군을 가르는 규칙이 여기 한 곳에 있다.
 * 종전엔 {@code isDebtType} 이 {@code AssetBalanceHistoryService} 안에 private 으로 숨어 있어
 * 요약 계산이 쓰지 못했고, 그래서 요약은 <b>부호로</b> 자산/부채를 갈랐다 — 잔액이 음수인
 * 입출금(마이너스 통장)이 부채로 넘어가 홈과 자산 화면의 총자산이 그 금액만큼 어긋났다.
 *
 * <p>마이너스 통장에 새 {@link AssetType} 을 만들지 않는다(사용자 확정). 저장은
 * {@code BANK_ACCOUNT} + 음수 잔액이고, 그래서 <b>집계 소속은 자산군</b>(총자산을 깎는다)
 * 이면서 <b>저장 부호는 음수</b>다 — 두 규칙이 서로 다른 질문에 답한다.
 */
public final class AssetSignPolicy {

    private AssetSignPolicy() {}

    /**
     * 잔액을 음수(빚)로 관리하는 부채군.
     *
     * <p>체크카드는 자체 잔액이 없어(연결 계좌에서 즉시 빠진다) 자산군이다.
     * 마이너스 통장은 {@code BANK_ACCOUNT} 라 여기 안 들어온다 — 음수 잔액인 채 자산군에 남는다.
     */
    public static boolean isDebtType(AssetType type) {
        return type == AssetType.CREDIT_CARD || type == AssetType.LOAN;
    }

    /**
     * API 경계에서 사용자가 넣은 잔액에 부호를 씌운다.
     *
     * <p>부채군은 무조건 음수, 마이너스 통장({@code isOverdraft=true})도 음수, 나머지 자산군은 양수다.
     * 여러 번 적용해도 결과가 같다(멱등) — {@code -Math.abs}/{@code Math.abs} 라 두 번 뒤집히지 않는다.
     *
     * <p><b>{@code isOverdraft} 가 null 이면 보낸 부호를 그대로 존중한다.</b> 이 필드를 모르는
     * 옛 클라이언트(현재 앱)를 위한 폴백이다. 여기서 무조건 {@code abs()} 를 걸면 옛 앱이
     * 마이너스 통장을 열어 저장만 해도 −50,000 이 +50,000 으로 뒤집혀 100,000 이 소리 없이
     * 움직인다 — 눈에 보이지 않게 틀린 값이 되므로 이 폴백은 빼면 안 된다.
     * 옛 클라이언트에게는 종전과 완전히 같은 동작이고, 새 클라이언트는 항상 값을 보내
     * 부호를 시스템이 정하게 된다.
     */
    public static long normalizeBalance(AssetType type, Boolean isOverdraft, long amount) {
        if (isDebtType(type)) {
            return -Math.abs(amount);
        }
        if (isOverdraft == null) {
            return amount;
        }
        return isOverdraft ? -Math.abs(amount) : Math.abs(amount);
    }

    /**
     * 잔액 이력에 절대 앵커를 적재할 때의 정규화 — 부채군만 음수로 내린다.
     *
     * <p>{@link #normalizeBalance} 와 달리 자산군 값은 <b>손대지 않는다</b>. 여기서 {@code abs()} 를
     * 걸면 API 경계에서 이미 음수로 확정한 마이너스 통장 잔액이 다시 양수로 뒤집힌다.
     * 부호를 정하는 건 경계이고, 여기는 <b>부채군이 양수로 새는 것만</b> 막는 마지막 방어선이다
     * (스케줄러·가져오기처럼 컨트롤러를 안 거치는 경로가 있다).
     */
    public static long normalizeAnchor(AssetType type, long amount) {
        return isDebtType(type) ? -Math.abs(amount) : amount;
    }
}
