package com.porest.desk.timer.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.timer.type.TimerType;
import com.porest.desk.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "timer_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimerSession extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "timer_type", nullable = false, length = 20)
    private TimerType timerType;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_seconds", nullable = false)
    private Long durationSeconds;

    @Column(name = "target_seconds")
    private Long targetSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_completed", nullable = false, length = 1)
    private YNType isCompleted;

    @Column(name = "laps", columnDefinition = "TEXT")
    private String laps;

    public static TimerSession createSession(User user, TimerType timerType, String label,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             Long durationSeconds, Long targetSeconds,
                                             YNType isCompleted, String laps) {
        TimerSession session = new TimerSession();
        session.user = user;
        session.timerType = timerType;
        session.label = label;
        session.startTime = startTime;
        session.endTime = endTime;
        session.durationSeconds = durationSeconds;
        session.targetSeconds = targetSeconds;
        session.isCompleted = isCompleted;
        session.laps = laps;
        return session;
    }
}
