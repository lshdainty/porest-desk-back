package com.porest.desk.dataimport.sms.repository;

import com.porest.core.type.YNType;
import com.porest.desk.dataimport.sms.domain.QSmsCardMapping;
import com.porest.desk.dataimport.sms.domain.SmsCardMapping;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class SmsCardMappingQueryDslRepository implements SmsCardMappingRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QSmsCardMapping mapping = QSmsCardMapping.smsCardMapping;

    @Override
    public Optional<SmsCardMapping> findByCardHintIncludingDeleted(Long userRowId, String cardHint) {
        SmsCardMapping result = queryFactory.selectFrom(mapping)
            .where(
                mapping.userRowId.eq(userRowId),
                mapping.cardHint.eq(cardHint)
            )
            .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public List<SmsCardMapping> findAllActiveByUser(Long userRowId) {
        return queryFactory.selectFrom(mapping)
            .where(
                mapping.userRowId.eq(userRowId),
                mapping.isDeleted.eq(YNType.N)
            )
            .orderBy(mapping.cardHint.asc())
            .fetch();
    }

    @Override
    public Optional<SmsCardMapping> findActiveById(Long rowId) {
        SmsCardMapping result = queryFactory.selectFrom(mapping)
            .where(
                mapping.rowId.eq(rowId),
                mapping.isDeleted.eq(YNType.N)
            )
            .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public SmsCardMapping save(SmsCardMapping entity) {
        entityManager.persist(entity);
        return entity;
    }
}
