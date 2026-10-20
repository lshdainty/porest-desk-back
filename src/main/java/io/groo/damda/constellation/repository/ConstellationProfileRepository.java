package io.groo.damda.constellation.repository;

import io.groo.damda.constellation.domain.ConstellationProfile;

import java.util.Optional;

public interface ConstellationProfileRepository {
    Optional<ConstellationProfile> findByUser(Long userRowId);
    ConstellationProfile save(ConstellationProfile profile);
}
