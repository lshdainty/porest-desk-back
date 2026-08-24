package com.porest.desk.stock.client.parser;

import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.type.MasterFile;

import java.util.List;

/**
 * 마스터파일 하나를 종목 레코드로 정규화한다.
 *
 * <p><b>왜 나눠 두는가</b> — 예전에는 파일 하나에 국내주식·업종지수·해외 규칙이 {@code switch}
 * 로 뒤엉켜 있었다. 소스가 하나뿐일 땐 견뎠지만 NH 포맷 두 개가 더 들어오면 한 포맷을 고칠 때
 * 다른 포맷이 깨질 자리가 생긴다. 포맷마다 파일을 나누면 한쪽을 고쳐도 다른 쪽이 안 흔들린다.
 *
 * <p><b>확장 방법</b> — 새 포맷을 {@code MasterFileFormat} 에 추가하고
 * {@code AbstractMasterFileParser} 를 상속한 구현에 {@code @Component} 를 달면 끝이다.
 * {@link InstrumentMasterParsers} 가 자동으로 주워 가고, 포맷에 구현이 없으면 기동할 때 터진다.
 */
public interface InstrumentMasterParser {

    /** 이 파서가 맡는 파일인가. 대개 포맷 한 가지를 본다. */
    boolean supports(MasterFile file);

    /**
     * 파싱 순서 — 작을수록 먼저 본다.
     *
     * <p>특정 파일만 규칙이 유별나면 그 파일 전용 파서를 낮은 순위로 넣으면 되고,
     * 포맷별 일반 파서는 {@code DEFAULT_PRIORITY} 를 쓴다.
     */
    int priority();

    /** 원본 바이트를 그대로 받는다 — 문자열로 먼저 바꾸면 고정폭 바이트 경계가 깨진다. */
    List<InstrumentRecord> parse(MasterFile file, byte[] raw);
}
