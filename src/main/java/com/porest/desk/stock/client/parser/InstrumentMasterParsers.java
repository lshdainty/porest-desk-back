package com.porest.desk.stock.client.parser;

import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.exception.KisMasterFileException;
import com.porest.desk.stock.type.MasterFile;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 파일에 맞는 파서를 골라 파싱을 넘기는 진입점.
 *
 * <p>{@link InstrumentMasterParser#supports} 가 참인 파서 중 {@code priority} 가 가장 낮은
 * 하나가 맡는다. 특정 파일만 규칙이 유별나면 그 파일 전용 파서를 낮은 순위로 넣으면 되고,
 * 기존 포맷 파서는 손대지 않는다.
 *
 * <p><b>파서를 늘리는 법</b> — 구현에 {@code @Component} 를 달면 자동 등록된다. 대신 기동 시
 * {@link MasterFile} 전 값에 맡을 파서가 있는지 확인한다 — 자동 수집은 빠진 걸 못 알아채는 게
 * 약점이라, 새 파일을 enum 에만 넣고 파서를 안 만들면 <b>동기화 배치가 도는 새벽이 아니라
 * 기동할 때</b> 터지게 했다.
 */
@Slf4j
@Component
public class InstrumentMasterParsers {

    private final List<InstrumentMasterParser> parsers;

    public InstrumentMasterParsers(List<InstrumentMasterParser> parsers) {
        this.parsers = parsers.stream()
            .sorted(Comparator.comparingInt(InstrumentMasterParser::priority))
            .toList();
    }

    @PostConstruct
    void verifyEveryFileCovered() {
        List<MasterFile> uncovered = Arrays.stream(MasterFile.values())
            .filter(f -> find(f) == null)
            .toList();
        if (!uncovered.isEmpty()) {
            throw new IllegalStateException("파서가 없는 마스터파일: " + uncovered);
        }
        log.info("마스터파일 파서 {}개 등록 — 파일 {}개 커버", parsers.size(), MasterFile.values().length);
    }

    public List<InstrumentRecord> parse(MasterFile file, byte[] raw) {
        InstrumentMasterParser parser = find(file);
        if (parser == null) {
            throw new KisMasterFileException("맡을 파서가 없는 마스터파일: " + file);
        }
        return parser.parse(file, raw);
    }

    private InstrumentMasterParser find(MasterFile file) {
        return parsers.stream().filter(p -> p.supports(file)).findFirst().orElse(null);
    }
}
