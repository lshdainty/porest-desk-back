package io.groo.damda.namu.service;

import io.groo.core.exception.InvalidValueException;
import io.groo.damda.common.exception.DeskErrorCode;
import io.groo.damda.namu.client.NamuApiClient;
import io.groo.damda.namu.client.NamuRateLimitException;
import io.groo.damda.namu.client.dto.NamuEnvelope;
import io.groo.damda.namu.client.dto.NamuListEnvelope;
import io.groo.damda.namu.client.dto.NamuPagedEnvelope;
import io.groo.damda.namu.dto.NamuAccountDetailDto;
import io.groo.damda.namu.client.dto.NamuResponse;
import io.groo.damda.securities.config.NamuProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * {@link NamuAccountDetailQueryService} 구현. 응답 칸은 모두 null 허용이고 숫자는 문자열 그대로다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NamuAccountDetailQueryServiceImpl implements NamuAccountDetailQueryService {

    private static final String ASSET_PATH = "/krstock/inquiry/v1/assetStatus";
    private static final String KR_PNL_PATH = "/krstock/inquiry/v1/tradingPnl";
    private static final String GB_PNL_PATH = "/gbstock/inquiry/v1/periodPnl";
    private static final String RIGHTS_SCHEDULED_PATH = "/krstock/inquiry/v1/rightsScheduled";
    private static final String RIGHTS_HELD_PATH = "/krstock/inquiry/v1/rightsHeld";

    public static final String KIND_SCHEDULED = "scheduled";
    public static final String KIND_HELD = "held";

    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final ParameterizedTypeReference<NamuEnvelope<NamuAccountDetailDto.AssetStatusRaw>> ASSET_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<
        NamuPagedEnvelope<NamuAccountDetailDto.KrPnlSummary, NamuAccountDetailDto.KrPnlRaw>> KR_PNL_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<
        NamuPagedEnvelope<NamuAccountDetailDto.UsPnlSummary, NamuAccountDetailDto.UsPnlRaw>> GB_PNL_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<
        NamuListEnvelope<NamuAccountDetailDto.RightScheduledRaw>> SCHEDULED_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<
        NamuPagedEnvelope<NamuAccountDetailDto.RightHeldSummary, NamuAccountDetailDto.RightHeldRaw>> HELD_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final NamuApiClient namuApiClient;
    private final NamuQueryService namuQueryService;
    private final NamuProperties namuProperties;
    private final Clock clock;

    /** 연속조회로 모은 결과. {@code truncated} 는 최대 쪽 수에서 끊겼다는 뜻. */
    private record Collected<S, I>(S summary, List<I> items, boolean truncated) {
    }

    /**
     * 연속조회를 따라가며 줄을 모은다. 문서: {@code cts}·{@code cts_flag} 는 <b>응답 헤더</b>, {@code Y}=다음 쪽 ·
     * {@code N}=끝, 다음 요청 헤더에 그대로 싣는다, {@code cts} 가 직전과 같으면 중단(전체 문자열 비교).
     * 최대 쪽 수({@code app.namu.max-continuation-pages})를 넘으면 멈추고 잘림을 알린다. 요약은 처음 온 것.
     */
    private <R extends NamuResponse, S, I> Collected<S, I> collect(
            Long userRowId, String path, Map<String, String> input, ParameterizedTypeReference<R> typeRef,
            Function<R, S> summaryOf, Function<R, List<I>> itemsOf) {
        S summary = null;
        List<I> all = new ArrayList<>();
        String cts = null;
        String flag = null;
        int max = Math.max(1, namuProperties.getMaxContinuationPages());
        for (int page = 1; ; page++) {
            NamuApiClient.NamuPage<R> res;
            try {
                res = namuApiClient.exchangePage(userRowId, path, input, typeRef, cts, flag);
            } catch (NamuRateLimitException e) {
                if (page == 1) {
                    throw e;
                }
                // 모은 데까지 내고 잘렸다고 알린다(429 를 맞은 쪽 이후는 못 읽었다).
                log.warn("나무 연속조회 {}쪽에서 유량 제한(429) - 모은 {}쪽까지 내고 잘림 표시: path={}, userRowId={}",
                    page, page - 1, path, userRowId);
                return new Collected<>(summary, all, true);
            }
            R body = res.body();
            if (summary == null) {
                summary = summaryOf.apply(body);
            }
            all.addAll(itemsOf.apply(body));
            boolean more = "Y".equalsIgnoreCase(res.ctsFlag()) && res.cts() != null
                && !res.cts().isBlank() && !res.cts().equals(cts);
            if (!more) {
                // 헤더 없이 응답코드(00165 · 00218)로만 "다음 쪽이 있다" 고 알리는 API — 따라갈 키가 없다.
                String code = body.effectiveCode();
                if (code != null && NamuResponse.CONTINUATION_CODES.contains(code.trim())) {
                    log.warn("나무 연속조회가 코드({})로만 알려 첫 쪽까지만 읽는다 - 잘림 표시: path={}, userRowId={}",
                        code.trim(), path, userRowId);
                    return new Collected<>(summary, all, true);
                }
                return new Collected<>(summary, all, false);
            }
            if (page >= max) {
                log.warn("나무 연속조회가 최대 {}쪽에서 끊긴다 - 합계가 덜 셀 수 있다: path={}, userRowId={}",
                    max, path, userRowId);
                return new Collected<>(summary, all, true);
            }
            cts = res.cts();
            flag = res.ctsFlag();
            pause();
        }
    }

    /** 쪽 사이 간격 — 문서: 초당 5회 수준, 0.2초 이상 간격. */
    private void pause() {
        int delay = namuProperties.getContinuationPageDelayMs();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public NamuAccountDetailDto.AssetStatus getAssetStatus(Long userRowId) {
        String account = namuQueryService.resolveAccountNo(userRowId, null);
        if (account == null) {
            return new NamuAccountDetailDto.AssetStatus(null, null, null, null, null);
        }
        Map<String, String> input = new LinkedHashMap<>();
        input.put("act_no", account);
        input.put("eal_aly_cd", "2");   // 시가평가
        input.put("aet_bse", "2");      // 총자산
        input.put("qut_dit_cd", "UNT"); // 통합시세
        input.put("aly_qut_cd", "1");   // 정규장
        NamuAccountDetailDto.AssetStatusRaw raw = namuApiClient.postObject(userRowId, ASSET_PATH, input, ASSET_TYPE);
        if (raw == null) {
            return new NamuAccountDetailDto.AssetStatus(null, null, null, null, null);
        }
        return new NamuAccountDetailDto.AssetStatus(blank(raw.totalAssets()), blank(raw.netAssets()),
            blank(raw.deposit()), blank(raw.depositD2()), blank(raw.withdrawable()));
    }

    @Override
    public NamuAccountDetailDto.RealizedPnl getRealizedPnl(Long userRowId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new InvalidValueException(DeskErrorCode.INVALID_DATE_VALUE);
        }
        String account = namuQueryService.resolveAccountNo(userRowId, null);
        if (account == null) {
            return new NamuAccountDetailDto.RealizedPnl(null, null, null, null, false);
        }

        boolean truncated = false;
        String krTotal = null;
        List<NamuAccountDetailDto.KrPnlItem> krItems = null;
        try {
            Map<String, String> input = new LinkedHashMap<>();
            input.put("act_no", account);
            input.put("iqr_sta_dt", YMD.format(from));
            input.put("iqr_end_dt", YMD.format(to));
            var res = collect(userRowId, KR_PNL_PATH, input, KR_PNL_TYPE,
                NamuPagedEnvelope::summary, NamuPagedEnvelope::items);
            truncated |= res.truncated();
            krTotal = res.summary() == null ? null : blank(res.summary().profit());
            // 판 것만 — 매도 수량 0 인 줄(산 것만 있는 종목)은 버린다. 국내 줄에는 매도 날짜가 없다(기간 합산).
            krItems = res.items().stream()
                .filter(r -> sold(r.sellQuantity()))
                .map(r -> new NamuAccountDetailDto.KrPnlItem(blank(r.symbol()), blank(r.name()),
                    blank(r.sellQuantity()), blank(r.profit()), blank(r.rate())))
                .toList();
        } catch (RuntimeException e) {
            log.warn("나무 실현손익(국내) 조회 실패 - 그쪽만 비운다: userRowId={}: {}", userRowId, e.getMessage());
        }

        String usTotal = null;
        List<NamuAccountDetailDto.UsPnlRow> usRows = null;
        try {
            Map<String, String> input = new LinkedHashMap<>();
            input.put("act_no", account);
            input.put("iqr_dit", "2");              // 원화 기준
            input.put("sta_orr_dt", YMD.format(from));
            input.put("end_orr_dt", YMD.format(to));
            input.put("iem_cd", "");
            input.put("trd_cur_cd", "USD");
            input.put("fc_sec_trd_nat_cd", "200");  // 미국
            var res = collect(userRowId, GB_PNL_PATH, input, GB_PNL_TYPE,
                NamuPagedEnvelope::summary, NamuPagedEnvelope::items);
            truncated |= res.truncated();
            usTotal = res.summary() == null ? null : blank(res.summary().profit());
            // 종목 이름 칸이 응답에 없다 — 날짜별 줄(Q3 결정).
            usRows = res.items().stream()
                .filter(r -> sold(r.sellQuantity()))
                .map(r -> new NamuAccountDetailDto.UsPnlRow(isoDate(r.date()), blank(r.sellQuantity()),
                    blank(r.profit()), blank(r.rate())))
                .toList();
        } catch (RuntimeException e) {
            log.warn("나무 실현손익(미국) 조회 실패 - 그쪽만 비운다: userRowId={}: {}", userRowId, e.getMessage());
        }
        return new NamuAccountDetailDto.RealizedPnl(krTotal, krItems, usTotal, usRows, truncated);
    }

    @Override
    public NamuAccountDetailDto.Rights getRights(Long userRowId, String kind, LocalDate from) {
        boolean held;
        if (KIND_SCHEDULED.equals(kind)) {
            held = false;
        } else if (KIND_HELD.equals(kind)) {
            held = true;
        } else {
            throw new InvalidValueException(DeskErrorCode.INVALID_PARAMETER_VALUE);
        }
        String account = namuQueryService.resolveAccountNo(userRowId, null);
        if (account == null) {
            return new NamuAccountDetailDto.Rights(List.of(), false);
        }
        if (!held) {
            var res = collect(userRowId, RIGHTS_SCHEDULED_PATH, Map.of("act_no", account), SCHEDULED_TYPE,
                r -> (Void) null, NamuListEnvelope::resultOrEmpty);
            return new NamuAccountDetailDto.Rights(res.items().stream()
                .map(r -> new NamuAccountDetailDto.Right(blank(r.symbol()), blank(r.name()), blank(r.typeCode()),
                    blank(r.typeName()), isoDate(r.baseDate()), blank(r.quantity()), null, null))
                .toList(), res.truncated());
        }
        // 지난 것 — 올해 1월 1일부터(화면이 from 을 안 주면). rit_tp_cd "" 는 전체.
        LocalDate start = from != null ? from : LocalDate.now(clock.withZone(SEOUL)).withDayOfYear(1);
        Map<String, String> input = new LinkedHashMap<>();
        input.put("sta_dt", YMD.format(start));
        input.put("act_no", account);
        input.put("rit_tp_cd", "");
        var res = collect(userRowId, RIGHTS_HELD_PATH, input, HELD_TYPE,
            NamuPagedEnvelope::summary, NamuPagedEnvelope::items);
        return new NamuAccountDetailDto.Rights(res.items().stream()
            // 지난 것은 유형 이름이 없다 — 화면이 typeCode 로 이름을 붙인다.
            .map(r -> new NamuAccountDetailDto.Right(blank(r.symbol()), blank(r.name()), blank(r.typeCode()),
                null, isoDate(r.baseDate()), blank(r.quantity()), blank(r.amount()), isoDate(r.payDate())))
            .toList(), res.truncated());
    }

    private static boolean sold(String qty) {
        BigDecimal v = NamuQueryServiceImpl.decimal(qty);
        return v != null && v.signum() != 0;
    }

    private static String blank(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    /** {@code yyyyMMdd} → {@code yyyy-MM-dd}. 못 읽으면 null. */
    private static String isoDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), YMD).toString();
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }
}
