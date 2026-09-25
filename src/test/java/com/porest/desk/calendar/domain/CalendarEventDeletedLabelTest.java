package com.porest.desk.calendar.domain;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라벨을 지우면 그 라벨을 단 일정은 "라벨 없음" 이다(QA 30 7).
 *
 * <p>라벨 삭제는 라벨 행만 지우고 일정의 참조는 그대로 둔다. 확인창은 "라벨 없음 상태가
 * 됩니다" 인데 일정 조회는 지운 라벨의 이름·색을 계속 내보냈다. 응답과 수정의 "안 온 칸은
 * 유지" 가 모두 살아 있는 라벨만 본다 — 이미 지워진 라벨을 가리키던 일정도 함께 풀린다.
 */
class CalendarEventDeletedLabelTest {

    private final User user = User.createUser(null, "tester", "테스터", "tester@porest.com");

    private CalendarEvent eventWith(EventLabel label) {
        ReflectionTestUtils.setField(user, "rowId", 1L);
        CalendarEvent event = CalendarEvent.createEvent(user, "회의", null, null, null,
            LocalDateTime.of(2026, 9, 25, 10, 0), LocalDateTime.of(2026, 9, 25, 11, 0),
            YNType.N, label, null, null, null);
        ReflectionTestUtils.setField(event, "rowId", 9L);
        return event;
    }

    @Test
    @DisplayName("지운 라벨 — 응답의 라벨 id·이름·색이 모두 비고, 살아 있는 라벨은 그대로")
    void deletedLabelIsNoLabel() {
        EventLabel label = EventLabel.createLabel(user, "업무", "#ff0000", 0);
        ReflectionTestUtils.setField(label, "rowId", 37L);
        CalendarEvent event = eventWith(label);

        assertThat(CalendarEventServiceDto.EventInfo.from(event, List.of()).labelRowId()).isEqualTo(37L);

        label.deleteLabel();

        var info = CalendarEventServiceDto.EventInfo.from(event, List.of());
        assertThat(event.getActiveLabel()).isNull();
        assertThat(info.labelRowId()).isNull();
        assertThat(info.labelName()).isNull();
        assertThat(info.labelColor()).isNull();
    }
}
