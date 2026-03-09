package com.porest.desk.calendar.domain;

import com.porest.core.type.YNType;
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
    @Column(name = "is_recurring", nullable = false, length = 1)
    private YNType isRecurring;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Holiday create(LocalDate holidayDate, String holidayName, HolidayType holidayType, YNType isRecurring) {
        Holiday holiday = new Holiday();
        holiday.holidayDate = holidayDate;
        holiday.holidayName = holidayName;
        holiday.holidayType = holidayType;
        holiday.isRecurring = isRecurring != null ? isRecurring : YNType.N;
        holiday.isDeleted = YNType.N;
        return holiday;
    }

    public void update(LocalDate holidayDate, String holidayName, HolidayType holidayType, YNType isRecurring) {
        this.holidayDate = holidayDate;
        this.holidayName = holidayName;
        this.holidayType = holidayType;
        this.isRecurring = isRecurring;
    }

    public void delete() {
        this.isDeleted = YNType.Y;
    }
}
