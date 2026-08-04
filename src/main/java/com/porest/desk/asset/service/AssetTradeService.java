package com.porest.desk.asset.service;

import com.porest.desk.asset.service.dto.AssetTradeServiceDto;

import java.util.List;

/**
 * 투자 자산의 매수·매도.
 *
 * <p>예수금이 줄고 느는 진짜 사건을 기록한다. 평가액 갱신으로 예수금을 추측하지 않는다.
 */
public interface AssetTradeService {

    /** 매수·매도·기초보유 등록. 예수금 flow·보유 수량/원가·(매도)실현손익을 함께 처리한다. */
    AssetTradeServiceDto.TradeInfo createTrade(AssetTradeServiceDto.CreateTradeCommand command);

    /** 거래 취소 — 예수금·수량·원가를 그 거래가 남긴 변동분만큼 정확히 되돌린다. */
    void deleteTrade(Long tradeRowId, Long userRowId);

    /** 자산의 거래 내역 (최신순). */
    List<AssetTradeServiceDto.TradeInfo> getTrades(Long assetRowId, Long userRowId);
}
