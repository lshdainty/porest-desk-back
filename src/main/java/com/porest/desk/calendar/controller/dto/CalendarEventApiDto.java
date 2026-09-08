package com.porest.desk.calendar.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
import com.porest.desk.calendar.service.dto.EventReminderServiceDto;
import com.porest.desk.calendar.type.CalendarEventType;
import com.porest.desk.common.validation.ColorFormat;
import com.porest.desk.common.validation.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class CalendarEventApiDto {

    @Schema(name = "CalendarEventCreateRequest")
    public record CreateRequest(
        @NotBlank(message = "일정 제목을 입력해 주세요")
        @Size(max = FieldLimits.TITLE_MAX, message = "제목은 200자까지 입력할 수 있어요")
        String title,
        @Size(max = FieldLimits.CONTENT_MAX, message = "설명은 10,000자까지 입력할 수 있어요")
        String description,
        CalendarEventType eventType,
        // calendar_event.color 는 varchar(20) 이지만 들어오는 값은 "#RRGGBB" 한 벌뿐이다.
        @Pattern(regexp = ColorFormat.HEX_RGB, message = ColorFormat.MESSAGE)
        String color,
        // 시작·종료는 NOT NULL 이고 서비스가 둘을 비교한다. 안 보내면 종전엔 NPE → 500 이었다(QA #81).
        @NotNull(message = "시작 일시를 입력해 주세요")
        LocalDateTime startDate,
        @NotNull(message = "종료 일시를 입력해 주세요")
        LocalDateTime endDate,
        YNType isAllDay,
        Long labelRowId,
        @Size(max = FieldLimits.SHORT_NOTE_MAX, message = "장소는 500자까지 입력할 수 있어요")
        String location,
        @Size(max = FieldLimits.SHORT_NOTE_MAX, message = "반복 규칙이 너무 길어요")
        String rrule,
        /**
         * 알림 사전분 목록. 원소가 null 이면 {@code minutes_before}(NOT NULL) 에 그대로 내려가
         * 500 이 났다 — 여기서 400 으로 끊는다. 같은 값이 두 번 담겨 와도 서버가 하나로 접는다.
         */
        List<@NotNull(message = "알림 시각이 비어 있어요") Integer> reminderMinutes,
        Long calendarRowId
    ) {}

    /**
     * 수정 본문 — <b>널 허용 칸은 실렸을 때만 바꾼다</b>(QA #96 의 계약을 캘린더로 넓힌다).
     *
     * <p>{@code Optional} 참조가 {@code null} 이면 키가 없었던 것(유지),
     * {@code Optional.empty()} 면 {@code null} 이 실린 것(지움)이다
     * ({@code AbsentAwareOptionalModule}). 종전엔 안 보낸 {@code description}·{@code color}·
     * {@code labelRowId}·{@code location}·{@code rrule} 이 null 로 덮여 사라졌다 —
     * 특히 {@code rrule} 은 앱 저장 경로에 파라미터조차 없어, 웹에서 만든 반복 일정이
     * 앱에서 한 번 저장되는 순간 반복을 잃었고 되살릴 화면이 없었다.
     *
     * <p>나머지 칸은 <b>종전 계약 그대로</b> 둔다 — 여기서 뜻이 바뀌면 화면이 조용히 깨진다.
     * <ul>
     *   <li>{@code title}·{@code eventType}·{@code isAllDay} — NOT NULL 이라 도메인이 이미
     *       null 을 무시한다(유지). 웹은 수정에서 {@code eventType} 을 일부러 빼고 보낸다(QA #89).</li>
     *   <li>{@code startDate}·{@code endDate} — {@code @NotNull} 이라 늘 실린다.</li>
     *   <li>{@code reminderMinutes} — 목록을 통째로 교체하는 칸이다. 종전부터
     *       "{@code null}=미변경, 리스트=교체" 였고, #96 도 거래 {@code splits}·자산
     *       {@code holdings}·할 일 {@code tagIds} 를 같은 이유로 그대로 뒀다.</li>
     *   <li>{@code calendarRowId} — 서비스가 이미 "null 이면 안 옮긴다" 로 읽는다.</li>
     * </ul>
     */
    @Schema(name = "CalendarEventUpdateRequest")
    public record UpdateRequest(
        @Size(max = FieldLimits.TITLE_MAX, message = "제목은 200자까지 입력할 수 있어요")
        String title,
        Optional<@Size(max = FieldLimits.CONTENT_MAX, message = "설명은 10,000자까지 입력할 수 있어요")
                 String> description,
        CalendarEventType eventType,
        // calendar_event.color 는 varchar(20) 이지만 들어오는 값은 "#RRGGBB" 한 벌뿐이다.
        Optional<@Pattern(regexp = ColorFormat.HEX_RGB, message = ColorFormat.MESSAGE)
                 String> color,
        // 시작·종료는 NOT NULL 이고 서비스가 둘을 비교한다. 안 보내면 종전엔 NPE → 500 이었다(QA #81).
        @NotNull(message = "시작 일시를 입력해 주세요")
        LocalDateTime startDate,
        @NotNull(message = "종료 일시를 입력해 주세요")
        LocalDateTime endDate,
        YNType isAllDay,
        Optional<Long> labelRowId,
        Optional<@Size(max = FieldLimits.SHORT_NOTE_MAX, message = "장소는 500자까지 입력할 수 있어요")
                 String> location,
        Optional<@Size(max = FieldLimits.SHORT_NOTE_MAX, message = "반복 규칙이 너무 길어요")
                 String> rrule,
        /**
         * 알림 사전분 목록. 원소가 null 이면 {@code minutes_before}(NOT NULL) 에 그대로 내려가
         * 500 이 났다 — 여기서 400 으로 끊는다. 같은 값이 두 번 담겨 와도 서버가 하나로 접는다.
         */
        List<@NotNull(message = "알림 시각이 비어 있어요") Integer> reminderMinutes,
        Long calendarRowId
    ) {}

    public record ReminderResponse(
        Long rowId,
        Long eventRowId,
        String reminderType,
        Integer minutesBefore,
        YNType isSent
    ) {
        public static ReminderResponse from(EventReminderServiceDto.ReminderInfo info) {
            return new ReminderResponse(
                info.rowId(),
                info.eventRowId(),
                info.reminderType(),
                info.minutesBefore(),
                info.isSent()
            );
        }
    }

    @Schema(name = "CalendarEventResponse")
    public record Response(
        Long rowId,
        Long userRowId,
        String title,
        String description,
        CalendarEventType eventType,
        String color,
        LocalDateTime startDate,
        LocalDateTime endDate,
        YNType isAllDay,
        Long labelRowId,
        String labelName,
        String labelColor,
        String location,
        String rrule,
        Long recurrenceId,
        YNType isException,
        List<ReminderResponse> reminders,
        Long calendarRowId,
        String calendarName,
        String calendarColor,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(CalendarEventServiceDto.EventInfo info) {
            List<ReminderResponse> reminderResponses = info.reminders() != null
                ? info.reminders().stream().map(ReminderResponse::from).toList()
                : List.of();
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.title(),
                info.description(),
                info.eventType(),
                info.color(),
                info.startDate(),
                info.endDate(),
                info.isAllDay(),
                info.labelRowId(),
                info.labelName(),
                info.labelColor(),
                info.location(),
                info.rrule(),
                info.recurrenceId(),
                info.isException(),
                reminderResponses,
                info.calendarRowId(),
                info.calendarName(),
                info.calendarColor(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    @Schema(name = "CalendarEventListResponse")
    public record ListResponse(
        List<Response> events
    ) {
        public static ListResponse from(List<CalendarEventServiceDto.EventInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }
}
