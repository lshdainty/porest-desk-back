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
        List<String> blockedParents,
        /**
         * 이대로 실행하면 <b>새로 만들어질</b> 카테고리 경로("대분류 &gt; 소분류", 최상위는 이름만).
         *
         * <p>가져오기는 파일에 있는 이름이 우리에게 없으면 묻지 않고 만든다. 오타(식비→싟비)가
         * 그대로 새 카테고리가 되므로, 실행 전에 무엇이 생기는지 보여 준다.
         * 실제로 만들지는 않는다(analyze 는 읽기 전용) — 해석만 그대로 돌려 본 결과다.
         *
         * <p>자동생성(autoCat)을 <b>켠 기준</b>으로 계산한다 — 이 목록이 그 토글을 끌지 말지의 판단 재료다.
         * 토글을 끄면 목록이 비는 것이 아니라 "미분류" 로 줄어든다(못 찾은 행이 거기로 가고,
         * 그 카테고리가 아직 없으면 그것도 새로 만들어진다).
         *
         * <p>길이는 상한이 있다 — 전체 개수는 {@link #newCategoryCount()} 를 봐라.
         */
        List<String> newCategories,
        /**
         * 새로 만들어질 카테고리의 <b>전체 개수</b>. {@code newCategories} 가 상한에서 잘려도 끝까지 센다.
         *
         * <p>카테고리 열을 잘못 매핑하면 행 수만큼 새 이름이 나온다 — 목록이 아니라 이 숫자가
         * "이대로 실행하면 안 되겠다" 를 말해 준다.
         */
        int newCategoryCount
    ) {}

    record ExecuteResult(
        /** 실제로 저장된 행 수. */
        int imported,
        /**
         * 넣지 않았지만 오류도 아닌 행 수 — <b>이체 + 중복 건너뜀</b>의 합.
         *
         * <p>뜻을 바꾸지 않는다(옛 화면이 이 숫자를 읽는다). 그중 중복이 몇 건인지는
         * {@link #duplicateSkipped()} 로 따로 알린다 — 예전엔 이 합계만 있어서
         * "가져오기는 됐다는데 그 행이 없다" 의 이유를 화면이 말해 줄 수 없었다.
         */
        int skipped,
        int failed,
        List<Failure> failures,
        /** 이번 실행에서 <b>실제로 만들어진</b> 카테고리 경로(생성 순서) — 상한까지. */
        List<String> createdCategories,
        /** 실제로 만들어진 카테고리의 전체 개수. 위 목록이 잘려도 끝까지 센다. */
        int createdCategoryCount,
        /**
         * 기존 거래와 겹쳐 건너뛴 행 수({@code skipped} 의 부분집합, {@code dupSkip} 일 때만 는다).
         *
         * <p>가장 흔한 오해가 여기서 난다 — 저장은 "성공" 했는데 방금 올린 행이 목록에 없다.
         * 몇 건이 왜 빠졌는지 숫자로 말해 준다.
         */
        int duplicateSkipped,
        /**
         * 그중 <b>어떤 행</b>이었는지 — 상한까지. 전체 개수는 {@link #duplicateSkipped()}.
         *
         * <p>숫자만으로는 파일을 고칠 수 없다. 줄번호·날짜·금액·거래처가 있어야
         * "이건 진짜 중복" 과 "이건 같은 날 두 번째 커피" 를 사용자가 가른다.
         */
        List<StandardRow> duplicates
    ) {}

    record Failure(int lineNo, String reason) {}
}
