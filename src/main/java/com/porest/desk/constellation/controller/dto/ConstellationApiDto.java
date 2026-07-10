package com.porest.desk.constellation.controller.dto;

import com.porest.desk.constellation.service.dto.ConstellationServiceDto;
import com.porest.desk.constellation.type.DailyStatus;

import java.time.LocalDate;
import java.util.List;

public class ConstellationApiDto {

    public record ConstellationResponse(
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
        public static ConstellationResponse from(ConstellationServiceDto.ConstellationInfo info) {
            return new ConstellationResponse(
                info.rowId(), info.constellationKey(), info.name(), info.nameEn(),
                info.description(), info.descriptionEn(),
                info.colorKey(), info.starCount(), info.starMap(), info.sortOrder()
            );
        }
    }

    public record CatalogResponse(List<ConstellationResponse> constellations) {
        public static CatalogResponse from(List<ConstellationServiceDto.ConstellationInfo> infos) {
            return new CatalogResponse(infos.stream().map(ConstellationResponse::from).toList());
        }
    }

    public record TodayResponse(
        ConstellationResponse constellation,
        int points,
        int goal,
        boolean collected,
        int todoPoints,
        int memoPoints,
        int streak,
        int guardCount,
        long totalCollected
    ) {
        public static TodayResponse from(ConstellationServiceDto.TodayInfo info) {
            return new TodayResponse(
                ConstellationResponse.from(info.constellation()),
                info.points(), info.goal(), info.collected(),
                info.todoPoints(), info.memoPoints(),
                info.streak(), info.guardCount(), info.totalCollected()
            );
        }
    }

    public record SkyDayResponse(
        LocalDate date,
        DailyStatus status,
        String constellationKey,
        String colorKey,
        int points,
        boolean guardUsed
    ) {
        public static SkyDayResponse from(ConstellationServiceDto.SkyDay day) {
            return new SkyDayResponse(
                day.date(), day.status(), day.constellationKey(), day.colorKey(), day.points(), day.guardUsed()
            );
        }
    }

    public record SkyResponse(List<SkyDayResponse> days) {
        public static SkyResponse from(List<ConstellationServiceDto.SkyDay> days) {
            return new SkyResponse(days.stream().map(SkyDayResponse::from).toList());
        }
    }

    public record CollectionEntryResponse(
        ConstellationResponse constellation,
        long collectCount,
        LocalDate lastCollectedDate
    ) {
        public static CollectionEntryResponse from(ConstellationServiceDto.CollectionEntry entry) {
            return new CollectionEntryResponse(
                ConstellationResponse.from(entry.constellation()),
                entry.collectCount(),
                entry.lastCollectedDate()
            );
        }
    }

    public record CollectionResponse(
        List<CollectionEntryResponse> entries,
        int collectedKinds,
        long totalCollected
    ) {
        public static CollectionResponse from(ConstellationServiceDto.CollectionInfo info) {
            return new CollectionResponse(
                info.entries().stream().map(CollectionEntryResponse::from).toList(),
                info.collectedKinds(),
                info.totalCollected()
            );
        }
    }
}
