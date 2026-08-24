package com.porest.desk.stock.client.parser;

import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.type.MasterFile;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 마스터파일 파싱의 공통 부분. 포맷별 구현이 이걸 상속해 쓴다.
 *
 * <p>여기 있는 것 — CP949 디코딩, 행 분리, 고정 레코드 분할, 깨진 행 건너뛰기와 집계,
 * 시장 내 심볼 중복 제거. 포맷이 달라도 같은 부분이라 한 벌만 둔다.
 *
 * <p>자식이 구현하는 것은 {@link #parseRecords} 하나다. 나머지는 부모가 처리하므로
 * 포맷을 늘려도 기존 파일을 손대지 않는다.
 *
 * <p><b>깨진 행에서 배치 전체를 죽이지 않는다.</b> 원본에 간혹 잘린 행이 섞이는데 예외를
 * 던지면 그날 동기화가 통째로 실패한다. 행 단위로 건너뛰고 건수만 남긴다 — 다만 조용히
 * 넘기지는 않는다. 건너뛴 수가 갑자기 늘면 구조가 바뀐 신호다.
 */
@Slf4j
public abstract class AbstractMasterFileParser implements InstrumentMasterParser {

    /** 포맷별 일반 파서의 기본 순위. 파일 전용 파서는 이보다 낮게 준다. */
    protected static final int DEFAULT_PRIORITY = 100;

    protected static final Charset CP949 = Charset.forName("MS949");

    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }

    @Override
    public final List<InstrumentRecord> parse(MasterFile file, byte[] raw) {
        Counters counters = new Counters();
        List<InstrumentRecord> records = parseRecords(file, raw, counters);
        return dedupe(file, records, counters);
    }

    /** 포맷별 실제 파싱. 건너뛴 행은 {@code counters.skip()} 으로 알린다. */
    protected abstract List<InstrumentRecord> parseRecords(MasterFile file, byte[] raw, Counters counters);

    /** 건너뛴 행 집계. 파서가 조용히 버리지 않게 하는 장치다. */
    protected static final class Counters {
        private int skipped;

        public void skip() {
            skipped++;
        }

        int skipped() {
            return skipped;
        }
    }

    // ── 포맷별 구현이 쓰는 도구 ─────────────────────────────────────────

    /** 행 단위 포맷용 — CP949 로 읽고 빈 줄을 버린다. */
    protected static List<String> lines(byte[] raw) {
        List<String> lines = new ArrayList<>();
        for (String line : new String(raw, CP949).split("\n")) {
            String stripped = line.replace("\r", "");
            if (!stripped.isBlank()) {
                lines.add(stripped);
            }
        }
        return lines;
    }

    /**
     * 고정 레코드 포맷용 — 레코드 크기로 자른다.
     *
     * <p>{@code 파일크기 % 레코드크기 != 0} 이면 구조가 바뀐 것이다. 그대로 자르면 전 레코드가
     * 밀려 그럴듯한 쓰레기가 나오므로 <b>한 건도 만들지 않고</b> 빈 목록을 돌려준다 —
     * 동기화는 빈 파일을 실패로 보고 기존 데이터를 유지한다.
     */
    protected static List<byte[]> records(MasterFile file, byte[] raw, int recordSize) {
        if (raw.length == 0 || raw.length % recordSize != 0) {
            log.error("마스터파일 레코드 경계 불일치 — 구조 개정 확인 필요: file={}, 크기={}, 레코드={}, 나머지={}",
                file, raw.length, recordSize, raw.length % recordSize);
            return List.of();
        }
        List<byte[]> records = new ArrayList<>(raw.length / recordSize);
        for (int i = 0; i < raw.length; i += recordSize) {
            byte[] record = new byte[recordSize];
            System.arraycopy(raw, i, record, 0, recordSize);
            records.add(record);
        }
        return records;
    }

    /** NH 국내 종목명 선두 1바이트는 지수 마커(* KOSPI200 / # 코스닥150)다. 정렬·검색에 섞이면 안 된다. */
    protected static String stripIndexMarker(String name) {
        if (name.isEmpty()) {
            return name;
        }
        char first = name.charAt(0);
        return (first == '*' || first == '#') ? name.substring(1).trim() : name.trim();
    }

    protected static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    // ── 공통 마무리 ───────────────────────────────────────────────────

    /** (시장, 심볼)이 유니크 키라 중복 행은 첫 행만 남긴다. 실측상 드물지만 원본 오염 방어다. */
    private List<InstrumentRecord> dedupe(MasterFile file, List<InstrumentRecord> records, Counters counters) {
        Map<String, InstrumentRecord> byKey = new LinkedHashMap<>();
        for (InstrumentRecord record : records) {
            byKey.putIfAbsent(record.market().name() + ':' + record.symbol(), record);
        }
        int duplicated = records.size() - byKey.size();
        if (counters.skipped() > 0 || duplicated > 0) {
            log.warn("마스터파일 파싱 - 건너뜀: file={}, 깨진행={}건, 중복={}건", file, counters.skipped(), duplicated);
        }
        return List.copyOf(byKey.values());
    }
}
