package io.groo.damda.dutchpay.repository;

import io.groo.core.type.YNType;
import io.groo.damda.dutchpay.domain.DutchPay;
import io.groo.damda.dutchpay.domain.DutchPayParticipant;
import io.groo.damda.dutchpay.domain.QDutchPay;
import io.groo.damda.dutchpay.domain.QDutchPayParticipant;
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
public class DutchPayQueryDslRepository implements DutchPayRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QDutchPay dutchPay = QDutchPay.dutchPay;
    private static final QDutchPayParticipant participant = QDutchPayParticipant.dutchPayParticipant;

    @Override
    public Optional<DutchPay> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(dutchPay)
                .leftJoin(dutchPay.participants).fetchJoin()
                .where(dutchPay.rowId.eq(rowId), dutchPay.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<DutchPayParticipant> findParticipantsByUser(Long userRowId) {
        return queryFactory.selectFrom(participant)
            .where(participant.user.rowId.eq(userRowId), participant.isDeleted.eq(YNType.N))
            .fetch();
    }

    @Override
    public List<DutchPay> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(dutchPay)
            .leftJoin(dutchPay.participants).fetchJoin()
            .where(dutchPay.user.rowId.eq(userRowId), dutchPay.isDeleted.eq(YNType.N))
            .orderBy(dutchPay.dutchPayDate.desc(), dutchPay.rowId.desc())
            .distinct()
            .fetch();
    }

    @Override
    public DutchPay save(DutchPay entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(DutchPay entity) {
        entityManager.remove(entity);
    }

    @Override
    public void flush() {
        entityManager.flush();
    }
}
