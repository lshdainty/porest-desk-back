package com.porest.desk.calendar.domain;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.type.HolidaySource;
import com.porest.desk.calendar.type.HolidayType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "holiday")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "holiday_name", nullable = false, length = 50)
    private String holidayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_type", nullable = false, length = 20)
    private HolidayType holidayType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private HolidaySource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Holiday create(LocalDate holidayDate, String holidayName, HolidayType holidayType,
                                 HolidaySource source) {
        Holiday holiday = new Holiday();
        holiday.holidayDate = holidayDate;
        holiday.holidayName = holidayName;
        holiday.holidayType = holidayType;
        holiday.source = source != null ? source : HolidaySource.MANUAL;
        holiday.isDeleted = YNType.N;
        return holiday;
    }

    /**
     * 외부 소스와 달라진 유형을 맞춘다.
     *
     * <p>(날짜, 이름)이 동기화 대조 키라 이름은 바뀌지 않고, 출처도 최초 적재 값을 유지한다.
     * 폴백으로 적재된 행이 KASI 복구 때마다 출처만 갱신되며 수정 이력을 더럽히는 것을 막기 위해서다.
     */
    public void syncType(HolidayType holidayType) {
        this.holidayType = holidayType;
    }

    public void delete() {
        this.isDeleted = YNType.Y;
    }

    public boolean isDeleted() {
        return YNType.Y == this.isDeleted;
    }

    /**
     * 자동 동기화가 관리하는 행인지. 수기 등록분과 사내 휴무는 외부 소스에 없어도 지우지 않는다.
     */
    public boolean isSyncManaged() {
        return this.source != HolidaySource.MANUAL && this.holidayType != HolidayType.CUSTOM;
    }
}
