package com.porest.desk.constellation.repository;

import com.porest.desk.constellation.domain.ConstellationProfile;

import java.util.Optional;

public interface ConstellationProfileRepository {
    Optional<ConstellationProfile> findByUser(Long userRowId);
    ConstellationProfile save(ConstellationProfile profile);
}
