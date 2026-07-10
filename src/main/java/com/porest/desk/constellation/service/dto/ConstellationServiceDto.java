package com.porest.desk.constellation.service.dto;

import com.porest.desk.constellation.domain.Constellation;
import com.porest.desk.constellation.type.DailyStatus;

import java.time.LocalDate;
import java.util.List;

public class ConstellationServiceDto {

    /** 별자리 마스터 정보 — starMap 은 {"pts":[[x,y]...],"edges":[[a,b]...]} JSON 문자열(클라이언트 파싱). */
    public record ConstellationInfo(
        Long rowId,
        String constellationKey,
        String name,
        String nameEn,
        String description,
        String descriptionEn,
        String colorKey,
        int starCount,
        String starMap,
        int sortOrder
    ) {
        public static ConstellationInfo from(Constellation constellation) {
            return new ConstellationInfo(
                constellation.getRowId(),
                constellation.getConstellationKey(),
                constellation.getName(),
                constellation.getNameEn(),
                constellation.getDescription(),
                constellation.getDescriptionEn(),
                constellation.getColorKey(),
                constellation.getStarCount(),
                constellation.getStarMap(),
                constellation.getSortOrder()
            );
        }
    }

    /** 오늘의 목표 현황 — points 는 유효 별빛 합, collected 는 오늘 수집 확정 여부. */
    public record TodayInfo(
        ConstellationInfo constellation,
        int points,
        int goal,
        boolean collected,
        int todoPoints,
        int memoPoints,
        int streak,
        int guardCount,
        long totalCollected
    ) {}

    /** 나의 밤하늘 하루 — 무행일은 REST 로 채워짐(constellationKey null). */
    public record SkyDay(
        LocalDate date,
        DailyStatus status,
        String constellationKey,
        String colorKey,
        int points,
        boolean guardUsed
    ) {}

    /** 도감 항목 — 미수집이면 collectCount 0, lastCollectedDate null. */
    public record CollectionEntry(
        ConstellationInfo constellation,
        long collectCount,
        LocalDate lastCollectedDate
    ) {}

    /** 도감 응답 — 전체 별자리 목록 + 수집 종수/누적 수. */
    public record CollectionInfo(
        List<CollectionEntry> entries,
        int collectedKinds,
        long totalCollected
    ) {}
}
