package com.porest.desk.dataimport.controller.dto;

import com.porest.desk.dataimport.type.ImportField;
import com.porest.desk.dataimport.type.ImportSource;

import java.util.List;
import java.util.Map;

/**
 * 데이터 가져오기 API 요청/응답 DTO.
 */
public class ImportApiDto {

    /** 원본 파일의 열(매핑 UI 의 select 옵션). */
    public record ColumnInfo(int index, String name) {}

    /**
     * 미리보기 한 행(매핑·정규화 적용 결과).
     *
     * <p>execute 응답의 "중복으로 건너뛴 행" 도 같은 모양을 쓴다 — 화면이 이미 그리는 표라
     * 새 컴포넌트가 필요 없다.
     */
    public record PreviewRow(
        int lineNo,
        String date,       // ISO, null 이면 파싱 실패
        String type,       // INCOME/EXPENSE, null 이면 미상
        Long amount,
        String category,
        String asset,
        String memo,
        boolean duplicate,
        String error,      // date/amount/type 등 오류 코드, null 이면 정상
        /** 거래처 — 중복 판정에 들어가는 값이라 "왜 중복인지" 를 보려면 필요하다. */
        String merchant
    ) {}

    /** analyze 응답. */
    public record AnalyzeResponse(
        String fileName,
        int totalRows,
        int validRows,
        int duplicateCount,
        List<ColumnInfo> columns,
        Map<ImportField, Integer> suggestedMapping,
        List<PreviewRow> preview,
        /** 거래가 달려 있어 하위를 만들 수 없는 대분류 — 비어 있지 않으면 그 행들이 전부 실패한다. */
        List<String> blockedParents,
        /**
         * 이대로 실행하면 새로 만들어질 카테고리 경로("대분류 &gt; 소분류", 최상위는 이름만).
         * 오타가 그대로 새 카테고리가 되므로 실행 전에 보여준다. 자동생성을 켠 기준.
         * <b>상한까지만</b> 담는다 — 전체 개수는 {@code newCategoryCount}.
         */
        List<String> newCategories,
        /** 새로 만들어질 카테고리의 전체 개수({@code newCategories.size()} 보다 클 수 있다). */
        int newCategoryCount
    ) {}

    /** execute 요청(JSON part). mapping: 필드→열인덱스. */
    public record ExecuteRequest(
        ImportSource source,
        Map<ImportField, Integer> mapping,
        boolean dupSkip,
        boolean autoCat
    ) {}

    /** execute 응답. */
    public record ExecuteResponse(
        int imported,
        int skipped,
        int failed,
        List<FailureRow> failures,
        /**
         * {@code failures} 가 잘렸는지. 서버는 실패 목록을 일정 수까지만 담는다 —
         * 잘렸다는 사실을 안 알리면 화면이 "실패 120" 이라 띄우고 50줄만 보여주게 된다.
         */
        boolean failuresTruncated,
        /** 이번 실행에서 실제로 만들어진 카테고리 경로(생성 순서) — 상한까지만 담는다. */
        List<String> createdCategories,
        /** 실제로 만들어진 카테고리의 전체 개수({@code createdCategories.size()} 보다 클 수 있다). */
        int createdCategoryCount,
        /**
         * 기존 거래와 겹쳐 <b>건너뛴</b> 행 수. {@code skipped}(이체 + 중복)의 부분집합이다.
         *
         * <p>화면은 이 숫자로 "중복으로 건너뜀 N건" 을 말한다 — 저장은 성공했다는데
         * 방금 올린 행이 목록에 없는 상황의 유일한 설명이다.
         */
        int duplicateSkipped,
        /** 그 행들 — <b>상한까지만</b> 담는다. 전체 개수는 {@code duplicateSkipped}. */
        List<PreviewRow> duplicates,
        /**
         * {@code duplicates} 가 잘렸는지. {@code failuresTruncated} 와 같은 이유 —
         * "중복 300건" 이라 띄우고 50줄만 보여주면 나머지를 조용히 잃는다.
         */
        boolean duplicatesTruncated
    ) {}

    /** 실패한 행. reason 은 화면 문구가 아니라 <b>사유 코드</b> 다(date/amount/type/parentHasTx/resolve/save). */
    public record FailureRow(int lineNo, String reason) {}
}
