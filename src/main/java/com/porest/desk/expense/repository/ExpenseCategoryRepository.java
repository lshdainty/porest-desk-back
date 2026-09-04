package com.porest.desk.expense.repository;

import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.type.ExpenseType;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository {
    Optional<ExpenseCategory> findById(Long rowId);
    List<ExpenseCategory> findAllByUser(Long userRowId);
    ExpenseCategory save(ExpenseCategory expenseCategory);
    void delete(ExpenseCategory expenseCategory);
    boolean hasChildren(Long categoryRowId);
    boolean existsActiveByUserAndParentAndTypeAndName(Long userRowId, Long parentRowId,
                                                      ExpenseType expenseType, String categoryName, Long excludeRowId);

    /**
     * 같은 자리(사용자 · 타입 · 부모)의 활성 카테고리를 이름으로 찾는다 — 가져오기가
     * "없으면 만들고 있으면 그걸 쓴다" 를 하려면 exists 가 아니라 <b>행</b>이 필요하다.
     *
     * <p>{@code parentRowId} 가 null 이면 최상위를 찾는다({@code parent IS NULL}). 유니크가
     * 붙기 전 데이터에는 같은 자리에 활성 행이 둘 이상 있을 수 있어 <b>첫 행(row_id 오름차순)</b>
     * 을 돌려준다 — 여기서 예외를 던지면 정리 전 데이터로 가져오기가 통째로 막힌다.
     */
    Optional<ExpenseCategory> findActiveByUserAndParentAndTypeAndName(Long userRowId, Long parentRowId,
                                                                      ExpenseType expenseType, String categoryName);

    /**
     * 지금까지의 변경을 즉시 내보낸다 — 활성 이름 UNIQUE 위반을 서비스 메서드 안에서
     * 잡기 위한 것이다. 명시하지 않으면 위반이 커밋 시점에 터져 try/catch 가 닿지 않는다.
     */
    void flush();
}
