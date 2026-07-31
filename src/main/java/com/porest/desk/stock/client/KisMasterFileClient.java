package com.porest.desk.stock.client;

import com.porest.desk.stock.client.dto.KisStockRecord;
import com.porest.desk.stock.config.KisProperties;
import com.porest.desk.stock.exception.KisMasterFileException;
import com.porest.desk.stock.type.StockMarket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * KIS 마스터파일을 내려받아 종목 레코드로 돌려준다.
 *
 * <p>파일은 zip 1개에 엔트리 1개, 본문은 CP949 인코딩이다. 인증이 없는 공개 다운로드라
 * 사용자 크리덴셜과 무관하게 서버가 직접 받는다.
 */
@Slf4j
@Component
public class KisMasterFileClient {

    private static final Charset CP949 = Charset.forName("MS949");

    private final RestTemplate restTemplate;
    private final KisProperties properties;

    public KisMasterFileClient(@Qualifier("kisRestTemplate") RestTemplate restTemplate, KisProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public List<KisStockRecord> fetch(StockMarket market) {
        String url = properties.getBaseUrl() + "/" + market.getFileName();

        byte[] zip;
        try {
            zip = restTemplate.getForObject(url, byte[].class);
        } catch (RestClientException e) {
            throw new KisMasterFileException("KIS 마스터파일 다운로드 실패: market=" + market, e);
        }
        if (zip == null || zip.length == 0) {
            throw new KisMasterFileException("KIS 마스터파일 응답이 비어 있습니다: market=" + market);
        }

        String text = unzipFirstEntry(market, zip);
        List<KisStockRecord> records = KisMasterFileParser.parse(market, text);
        log.debug("KIS 마스터파일 수신: market={}, zip={}bytes, 종목={}건", market, zip.length, records.size());
        return records;
    }

    private String unzipFirstEntry(StockMarket market, byte[] zip) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                throw new KisMasterFileException("KIS 마스터파일 zip 에 엔트리가 없습니다: market=" + market);
            }
            return new String(zis.readAllBytes(), CP949);
        } catch (IOException e) {
            throw new KisMasterFileException("KIS 마스터파일 압축 해제 실패: market=" + market, e);
        }
    }
}
