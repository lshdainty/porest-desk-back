package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;

import java.util.List;

public interface ExpenseCategoryService {
    ExpenseCategoryServiceDto.CategoryInfo createCategory(ExpenseCategoryServiceDto.CreateCommand command);

    /**
     * 같은 자리(사용자 · 타입 · 부모)에 그 이름의 카테고리를 <b>확보</b>한다 — 없으면 만들고
     * 있으면 그 행을 돌려준다. 가져오기(dataimport)의 카테고리 자동 생성 전용 진입점이다.
     *
     * <p><b>왜 createCategory 로는 안 되는가.</b> 가져오기는 파일의 행마다 카테고리를 해석하고
     * 결과를 캐시에 담는다. {@code createCategory} 가 이름 중복으로 409 를 던지면 캐시가 안 채워져
     * <b>같은 카테고리를 쓰는 뒤의 모든 행이 같은 자리에서 다시 실패한다</b> — 카테고리 하나가
     * 파일의 절반을 차지하면 그 절반이 통째로 실패로 센다. 여기서 "있으면 그걸 쓴다" 로 받으면
     * 그 연쇄가 끊긴다.
     *
     * <p>활성 이름 UNIQUE 가 DB 에 붙으면 동시 가져오기 두 개가 같은 이름을 만들려다 한쪽이
     * 제약 위반으로 진다. 구현은 그때 <b>새 트랜잭션으로 한 번 더</b> 돌아 상대가 넣은 행을
     * 재조회해 돌려준다(1회 재시도 — 두 번째도 위반이면 우리가 모르는 상황이라 그대로 올린다).
     */
    ExpenseCategoryServiceDto.CategoryInfo findOrCreateCategory(ExpenseCategoryServiceDto.CreateCommand command);

    /**
     * 신규 사용자에게 기본 지출·수입 카테고리 세트를 만들어 준다.
     *
     * <p>카테고리가 하나도 없으면 거래 시트의 저장이 조용히 비활성이라 신규
     * 가입자는 거래를 기록할 수 없다. 기본 캘린더처럼 최초 프로비저닝
     * 시점(단일 트랜잭션)에 한 번 심는다.
     *
     * <p>멱등 — 활성 카테고리가 하나라도 있으면 아무것도 하지 않는다.
     * 호출처는 신규 사용자 프로비저닝 한 곳이라 "전부 지운 사용자" 가 다시
     * 시딩되는 경로는 없고, 이 검사는 동시 최초 로그인 경합의 이중 시딩을
     * 막는다.
     */
    void seedDefaults(Long userRowId);
    List<ExpenseCategoryServiceDto.CategoryInfo> getCategories(Long userRowId);
    ExpenseCategoryServiceDto.CategoryInfo updateCategory(Long categoryId, Long userRowId, ExpenseCategoryServiceDto.UpdateCommand command);

    /**
     * 카테고리에 달린 거래·반복거래·분할을 다른 카테고리로 일괄 이동한다.
     *
     * <p>거래가 직접 달린 카테고리는 부모가 될 수 없어 하위 분류를 만들 수 없다.
     * 그 상태를 푸는 유일한 방법이 거래를 다른 곳으로 옮기는 것인데, 지금까지는
     * 거래를 하나씩 편집하는 수밖에 없었다.
     */

    /**
     * 하위 카테고리를 만들면서 이 카테고리의 거래를 그리로 옮긴다.
     *
     * <p>거래가 달린 카테고리는 하위를 만들 수 없고, 옮길 하위가 없으면 거래도 못 옮기는
     * 교착이 생긴다. 생성과 이동을 한 트랜잭션으로 묶어 그 고리를 끊는다 —
     * 생성 시점엔 규칙 위반이지만 커밋 시점엔 부모에 직접 거래가 없어 정합하다.
     */
    ExpenseCategoryServiceDto.MoveResult moveTransactionsToNewChild(
            Long categoryId, String childName, String icon, String color, Long userRowId);

    ExpenseCategoryServiceDto.MoveResult moveTransactions(Long sourceCategoryId, Long targetCategoryId, Long userRowId);

    void deleteCategory(Long categoryId, Long userRowId);
    void reorderCategories(Long userRowId, List<ExpenseCategoryServiceDto.ReorderItem> items);
}
