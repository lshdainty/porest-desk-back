package com.porest.desk.dataimport.sms.service.dto;

import com.porest.desk.asset.type.AssetType;
import com.porest.desk.dataimport.sms.service.SmsConfidence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class SmsImportServiceDto {

    /**
     * 문자 한 통의 해석 결과 — 저장 전 미리보기.
     *
     * <p>파서가 뽑은 원시 값에 사용자 문맥(기억해 둔 카드 매핑·과거 카테고리)을 얹은 형태다.
     * 클라이언트는 이걸 폼에 그대로 채우고, 사용자가 확인·수정한 값으로 commit 을 부른다.
     *
     * @param matched          결제 문자로 인식했는가
     * @param cancel           취소 문자인가 — true 면 저장을 막는다(1차 범위)
     * @param assetRemembered  자산이 기억해 둔 매핑에서 나왔는가(= 사용자가 전에 확정한 것)
     * @param assetCandidates  매핑이 없을 때 사용자가 고를 후보
     */
    public record ParseResult(
        boolean matched,
        SmsConfidence confidence,
        boolean cancel,
        Long amount,
        String merchant,
        LocalDateTime occurredAt,
        Integer installmentMonths,
        String cardHint,
        String issuerName,
        String cardLast4,
        Long assetRowId,
        boolean assetRemembered,
        List<AssetCandidate> assetCandidates,
        Long categoryRowId,
        String categoryName,
        BigDecimal originalAmount,
        String originalCurrency
    ) {
        /** 결제 문자가 아님 — 나머지 필드는 비운다. */
        public static ParseResult noMatch() {
            return new ParseResult(false, SmsConfidence.LOW, false, null, null, null, null,
                null, null, null, null, false, List.of(), null, null, null, null);
        }
    }

    /** 자산 후보 — 어느 카드로 기록할지 사용자가 고르는 목록. */
    public record AssetCandidate(
        Long rowId,
        String assetName,
        String institution,
        AssetType assetType
    ) {}

    /**
     * 저장 명령 — 사용자가 확인·보정한 최종 값.
     *
     * <p>파싱 결과를 그대로 믿지 않고 클라이언트가 확정한 값을 받는다.
     * 파서가 틀렸을 때 사용자가 고친 내용이 반영되어야 하고,
     * 서버가 문자를 다시 파싱해 덮으면 그 수정이 사라진다.
     *
     * <p>원문({@code text})은 값을 뽑으려는 게 아니라 <b>가드</b>로 받는다 —
     * 서버가 다시 파싱해 취소 문자면 저장을 막고, 카드 매핑 키를 스스로 도출한다.
     * 매핑 키를 클라이언트가 정하게 두면 남의 문자에서 온 키로 자산을 묶을 수 있다.
     *
     * @param rememberCard  체크 시 (원문에서 도출한 카드 힌트 → assetRowId) 를 기억해 다음부터 자동 연결
     */
    public record CommitCommand(
        Long userRowId,
        String text,
        Long assetRowId,
        Long categoryRowId,
        Long amount,
        String merchant,
        String description,
        LocalDateTime expenseDate,
        /** 결제수단 코드(CASH/CARD/TRANSFER/OTHER). 비면 카드로 본다 — 카드 결제 문자이므로. */
        String paymentMethod,
        Integer installmentMonths,
        BigDecimal originalAmount,
        String originalCurrency,
        BigDecimal exchangeRate,
        boolean rememberCard
    ) {}

    /** 저장 결과 — 만들어진 지출과, 카드 매핑을 새로 기억했는지. */
    public record CommitResult(
        Long expenseRowId,
        boolean cardRemembered
    ) {}

    /** 카드 매핑 한 건 — 설정 화면 목록용. */
    public record CardMappingInfo(
        Long rowId,
        String cardHint,
        Long assetRowId,
        String assetName
    ) {}
}
