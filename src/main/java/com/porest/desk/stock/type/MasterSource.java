package com.porest.desk.stock.type;

/**
 * 종목 마스터파일을 주는 곳. 둘 다 인증 없는 공개 다운로드다.
 *
 * <p><b>시장코드 축은 KIS 로 고정한다.</b> 두 소스를 실제로 대사해 보면 같은 종목을 다르게
 * 분류한다 — KIS {@code AMSMST.COD} 4,657건 중 NH 는 2,700건을 NYY(NYSE), 1,599건을 BTQ 로
 * 넣는다. {@code stock_master} 의 유니크 키가 (market_code, symbol) 이라 NH 코드를 그대로
 * 적재하면 같은 종목이 두 행으로 갈라지고, 사용자가 자산에 연결해 둔 종목이 어느 행을
 * 가리키는지 깨진다.
 *
 * <p>그래서 NH 는 두 가지로만 쓴다 — ① KIS 가 안 주는 필드 보강 ② KIS 에 아예 없는 시장 추가.
 */
public enum MasterSource {

    /** 한국투자증권. 시장별 파일 15개, zip 압축. */
    KIS(true),

    /** NH투자증권(나무). 국내 1 + 해외 1, 비압축. 파일마다 {@code .h} 구조체 정의가 함께 배포된다. */
    NH(false);

    private final boolean zipped;

    MasterSource(boolean zipped) {
        this.zipped = zipped;
    }

    public boolean isZipped() {
        return zipped;
    }
}
