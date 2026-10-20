package io.groo.damda.constellation.repository;

import io.groo.core.type.YNType;
import io.groo.damda.constellation.domain.ConstellationProfile;
import io.groo.damda.constellation.domain.QConstellationProfile;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class ConstellationProfileQueryDslRepository implements ConstellationProfileRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QConstellationProfile profile = QConstellationProfile.constellationProfile;

    @Override
    public Optional<ConstellationProfile> findByUser(Long userRowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(profile)
                .where(profile.user.rowId.eq(userRowId), profile.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public ConstellationProfile save(ConstellationProfile entity) {
        entityManager.persist(entity);
        return entity;
    }
}
