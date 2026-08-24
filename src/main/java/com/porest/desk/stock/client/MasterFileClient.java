package com.porest.desk.stock.client;

import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.client.parser.InstrumentMasterParsers;
import com.porest.desk.stock.config.MasterFileProperties;
import com.porest.desk.stock.exception.KisMasterFileException;
import com.porest.desk.stock.type.MasterFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 마스터파일을 내려받아 종목 레코드로 돌려준다.
 *
 * <p>인증이 없는 공개 다운로드라 사용자 크리덴셜과 무관하게 서버가 직접 받는다.
 * KIS 는 zip 1개에 엔트리 1개, NH 는 비압축이다 — 그 차이는 {@code MasterSource} 가 안다.
 *
 * <p>본문을 <b>바이트로 넘긴다.</b> 문자열로 먼저 바꾸면 CP949 한글이 2바이트라 고정폭
 * 레코드 경계가 문자 단위로 밀린다(NH 파일이 그 포맷이다).
 */
@Slf4j
@Component
public class MasterFileClient {

    private final RestTemplate restTemplate;
    private final MasterFileProperties properties;
    private final InstrumentMasterParsers parsers;

    public MasterFileClient(@Qualifier("masterFileRestTemplate") RestTemplate restTemplate,
                            MasterFileProperties properties,
                            InstrumentMasterParsers parsers) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.parsers = parsers;
    }

    public List<InstrumentRecord> fetch(MasterFile file) {
        String baseUrl = properties.baseUrlOf(file.getSource());
        if (baseUrl == null) {
            throw new KisMasterFileException("마스터파일 base URL 미설정: source=" + file.getSource());
        }

        byte[] body;
        try {
            body = restTemplate.getForObject(baseUrl + "/" + file.getFileName(), byte[].class);
        } catch (RestClientException e) {
            throw new KisMasterFileException("마스터파일 다운로드 실패: file=" + file, e);
        }
        if (body == null || body.length == 0) {
            throw new KisMasterFileException("마스터파일 응답이 비어 있습니다: file=" + file);
        }

        byte[] raw = file.getSource().isZipped() ? unzipFirstEntry(file, body) : body;
        List<InstrumentRecord> records = parsers.parse(file, raw);
        log.debug("마스터파일 수신: file={}, 수신={}bytes, 본문={}bytes, 종목={}건",
            file, body.length, raw.length, records.size());
        return records;
    }

    private byte[] unzipFirstEntry(MasterFile file, byte[] zip) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                throw new KisMasterFileException("마스터파일 zip 에 엔트리가 없습니다: file=" + file);
            }
            return zis.readAllBytes();
        } catch (IOException e) {
            throw new KisMasterFileException("마스터파일 압축 해제 실패: file=" + file, e);
        }
    }
}
