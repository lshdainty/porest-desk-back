package com.porest.desk.card.repository;

import com.porest.core.type.YNType;
import com.porest.desk.card.domain.CardBenefitCategoryMapping;
import com.porest.desk.card.domain.QCardBenefitCategoryMapping;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class CardBenefitCategoryMappingQueryDslRepository implements CardBenefitCategoryMappingRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QCardBenefitCategoryMapping mapping = QCardBenefitCategoryMapping.cardBenefitCategoryMapping;

    @Override
    public Optional<CardBenefitCategoryMapping> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(mapping)
                .where(mapping.rowId.eq(rowId), mapping.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public Optional<CardBenefitCategoryMapping> findUserMapping(Long userRowId, String benefitCategory) {
        return Optional.ofNullable(
            queryFactory.selectFrom(mapping)
                .where(
                    mapping.user.rowId.eq(userRowId),
                    mapping.benefitCategory.eq(benefitCategory),
                    mapping.isDeleted.eq(YNType.N)
                )
                .fetchOne()
        );
    }

    @Override
    public List<CardBenefitCategoryMapping> findEffectiveMappings(Long userRowId) {
        // 공용(user IS NULL) + 해당 user 커스텀 모두 로드 후 benefit_category 단위로 merge
        List<CardBenefitCategoryMapping> all = queryFactory.selectFrom(mapping)
            .leftJoin(mapping.user).fetchJoin()
            .leftJoin(mapping.expenseCategory).fetchJoin()
            .where(
                mapping.isDeleted.eq(YNType.N),
                mapping.user.isNull().or(mapping.user.rowId.eq(userRowId))
            )
            .fetch();

        // 커스텀이 있으면 공용을 덮어씌움
        Map<String, CardBenefitCategoryMapping> merged = new HashMap<>();
        for (CardBenefitCategoryMapping m : all) {
            String key = m.getBenefitCategory();
            CardBenefitCategoryMapping prev = merged.get(key);
            if (prev == null) {
                merged.put(key, m);
            } else if (m.getUser() != null) {
                // 사용자 커스텀이 우선
                merged.put(key, m);
            }
        }
        return merged.values().stream()
            .sorted((a, b) -> a.getBenefitCategory().compareTo(b.getBenefitCategory()))
            .toList();
    }

    @Override
    public List<CardBenefitCategoryMapping> findAllDefaultMappings() {
        return queryFactory.selectFrom(mapping)
            .leftJoin(mapping.expenseCategory).fetchJoin()
            .where(
                mapping.user.isNull(),
                mapping.isDeleted.eq(YNType.N)
            )
            .orderBy(mapping.benefitCategory.asc())
            .fetch();
    }

    @Override
    public CardBenefitCategoryMapping save(CardBenefitCategoryMapping entity) {
        entityManager.persist(entity);
        return entity;
    }
}
