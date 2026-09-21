package com.porest.desk.card.service;

import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetPaymentDayHistory;
import com.porest.desk.asset.repository.AssetPaymentDayHistoryRepository;
import com.porest.desk.asset.type.AssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 신용카드의 회차별 결제일(D5) — 읽기(회차 계산)와 쓰기(카드 등록·결제일 변경).
 *
 * <p>회차 계산은 전부 {@link PaymentSchedule} 로 한다. 자산의 {@code payment_day} 는 "지금 결제일"
 * (새 회차에 쓸 값)이고, 지난 회차의 결제일은 이력이 정한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentScheduleService {

    private final AssetPaymentDayHistoryRepository historyRepository;

    /** 그 카드의 회차별 결제일. 신용카드가 아니면 null. */
    public PaymentSchedule scheduleOf(Asset card) {
        if (card == null || card.getAssetType() != AssetType.CREDIT_CARD) {
            return null;
        }
        List<PaymentSchedule.Entry> entries = historyRepository.findActiveByAsset(card.getRowId()).stream()
            .map(h -> new PaymentSchedule.Entry(h.getEffectiveFromPeriod(), h.getPaymentDay()))
            .toList();
        return new PaymentSchedule(entries, card.getPaymentDay());
    }

    /** 카드 등록(또는 신용카드로 바뀜) — 처음부터 이 결제일. 이미 이력이 있으면 그대로 둔다. */
    @Transactional
    public void initialize(Asset card) {
        if (card.getAssetType() != AssetType.CREDIT_CARD || card.getPaymentDay() == null
                || !historyRepository.findActiveByAsset(card.getRowId()).isEmpty()) {
            return;
        }
        historyRepository.save(AssetPaymentDayHistory.of(
            card, AssetPaymentDayHistory.FROM_THE_BEGINNING, card.getPaymentDay()));
    }

    /**
     * 결제일 변경 — <b>다음 회차부터</b>(D5). 지금 결제 전인 가장 가까운 회차는 옛 결제일에 결제된다.
     *
     * <p>{@code card} 는 아직 옛 결제일을 들고 있어야 한다(자산을 고치기 전에 부른다).
     *
     * @return 새 결제일이 효력을 갖는 첫 회차의 시작일(변경이 없으면 null)
     */
    @Transactional
    public LocalDate changePaymentDay(Asset card, Integer newDay, LocalDate today) {
        if (card.getAssetType() != AssetType.CREDIT_CARD || newDay == null) {
            return null;
        }
        Integer oldDay = card.getPaymentDay();
        List<AssetPaymentDayHistory> rows = historyRepository.findActiveByAsset(card.getRowId());
        if (oldDay == null && rows.isEmpty()) {
            // 결제일이 없던 카드 — 청구 회차가 없었으니 처음부터 이 결제일로 본다.
            historyRepository.save(AssetPaymentDayHistory.of(card, AssetPaymentDayHistory.FROM_THE_BEGINNING, newDay));
            return AssetPaymentDayHistory.FROM_THE_BEGINNING;
        }
        if (rows.isEmpty()) {
            // 이력이 생기기 전에 만든 카드 — 지금 결제일을 처음부터로 먼저 적는다.
            AssetPaymentDayHistory base = historyRepository.save(
                AssetPaymentDayHistory.of(card, AssetPaymentDayHistory.FROM_THE_BEGINNING, oldDay));
            rows = List.of(base);
        }
        PaymentSchedule current = new PaymentSchedule(rows.stream()
            .map(h -> new PaymentSchedule.Entry(h.getEffectiveFromPeriod(), h.getPaymentDay()))
            .toList(), oldDay);
        LocalDate upcoming = current.firstOpenPaymentDate(today);
        LocalDate from = PaymentSchedule.cycleOfPaymentDate(upcoming).plusMonths(1);
        if (newDay.equals(current.dayFor(from))) {
            return null;
        }
        // 같은 회차 이후를 정하던 이전 변경은 물린다 — 마지막 변경이 이긴다.
        for (AssetPaymentDayHistory h : rows) {
            if (!h.getEffectiveFromPeriod().isBefore(from)) {
                h.delete();
            }
        }
        historyRepository.save(AssetPaymentDayHistory.of(card, from, newDay));
        log.info("카드 결제일 변경: assetId={}, {}일 → {}일, {} 회차부터", card.getRowId(), oldDay, newDay, from);
        return from;
    }
}
