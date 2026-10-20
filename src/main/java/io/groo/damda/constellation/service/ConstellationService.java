package io.groo.damda.constellation.service;

import io.groo.damda.constellation.service.dto.ConstellationServiceDto;

import java.util.List;

/** 별자리 조회 — 카탈로그/오늘의 목표/나의 밤하늘/도감. 적립은 {@link StarlightService}. */
public interface ConstellationService {

    ConstellationServiceDto.TodayInfo getToday(Long userRowId);

    List<ConstellationServiceDto.SkyDay> getSky(Long userRowId, int days);

    ConstellationServiceDto.CollectionInfo getCollection(Long userRowId);
}
