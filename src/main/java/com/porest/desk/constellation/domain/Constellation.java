package com.porest.desk.constellation.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 별자리 마스터 (시스템 공용 카탈로그) — 할일 게이미피케이션 수집 대상.
 * star_map: {"pts":[[x,y],...],"edges":[[a,b],...]} 0-100 정규 좌표 + 연결선 JSON.
 * 수집 목표 별빛 = starCount(= star_map.pts 길이).
 */
@Entity
@Table(name = "constellation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Constellation extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "constellation_key", nullable = false, length = 30, unique = true)
    private String constellationKey;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "name_en", nullable = false, length = 80)
    private String nameEn;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "description_en", length = 200)
    private String descriptionEn;

    @Column(name = "color_key", nullable = false, length = 20)
    private String colorKey;

    @Column(name = "star_count", nullable = false)
    private Integer starCount;

    @Column(name = "star_map", nullable = false, columnDefinition = "LONGTEXT")
    private String starMap;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_active", nullable = false, length = 1)
    private YNType isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Constellation createConstellation(String constellationKey, String name, String nameEn,
                                                    String description, String descriptionEn,
                                                    String colorKey, int starCount, String starMap, int sortOrder) {
        Constellation constellation = new Constellation();
        constellation.constellationKey = constellationKey;
        constellation.name = name;
        constellation.nameEn = nameEn;
        constellation.description = description;
        constellation.descriptionEn = descriptionEn;
        constellation.colorKey = colorKey;
        constellation.starCount = starCount;
        constellation.starMap = starMap;
        constellation.sortOrder = sortOrder;
        constellation.isActive = YNType.Y;
        constellation.isDeleted = YNType.N;
        return constellation;
    }
}
