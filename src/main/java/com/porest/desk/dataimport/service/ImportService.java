package com.porest.desk.dataimport.service;

import com.porest.desk.dataimport.type.ImportField;
import com.porest.desk.dataimport.type.ImportSource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 데이터 가져오기 — 파일 분석(미리보기)과 실제 저장(execute).
 *
 * <p>stateless: analyze/execute 모두 파일을 다시 받아 파싱한다(중간 서버 임시저장 없음).
 * execute 는 사용자가 매핑 UI 에서 보정한 최종 매핑을 받아 거래를 생성한다.
 */
public interface ImportService {

    /** 파일 파싱 + 소스별 자동매핑 제안 + 미리보기·중복·유효건수 산출(저장 없음). */
    AnalyzeResult analyze(MultipartFile file, ImportSource source, Long userRowId);

    /** 최종 매핑·옵션으로 거래 저장. */
    ExecuteResult execute(MultipartFile file, ImportSource source, Map<ImportField, Integer> mapping,
                          boolean dupSkip, boolean autoCat, Long userRowId);

    record AnalyzeResult(
        String fileName,
        int totalRows,
        int validRows,
        int duplicateCount,
        List<String> columns,
        Map<ImportField, Integer> suggestedMapping,
        List<StandardRow> preview,
        /**
         * 거래가 직접 달려 있어 자식을 만들 수 없는 대분류 이름들.
         * 비어 있지 않으면 그 대분류를 쓰는 행이 전부 실패하므로, 실행 전에 알려야 한다.
         */
        List<String> blockedParents
    ) {}

    record ExecuteResult(
        int imported,
        int skipped,
        int failed,
        List<Failure> failures
    ) {}

    record Failure(int lineNo, String reason) {}
}
