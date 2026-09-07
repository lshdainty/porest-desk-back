package com.porest.desk.memo.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.common.validation.ColorFormat;
import com.porest.desk.common.validation.FieldLimits;
import com.porest.desk.memo.service.dto.MemoServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class MemoApiDto {

    /**
     * {@code memo.title} 은 NOT NULL — 빠뜨리면 종전엔 409 "다른 곳에서 먼저 수정됐어요" 였다(QA #81).
     * 웹·앱 모두 편집기에서 빈 제목을 이미 막고 있어 필수로 걸어도 쓰던 화면이 막히지 않는다.
     */
    @Schema(name = "MemoCreateRequest")
    public record CreateRequest(
        @NotBlank(message = "메모 제목을 입력해 주세요")
        @Size(max = FieldLimits.TITLE_MAX, message = "제목은 200자까지 입력할 수 있어요")
        String title,
        @Size(max = FieldLimits.CONTENT_MAX, message = "본문은 10,000자까지 입력할 수 있어요")
        String content,
        @Size(max = FieldLimits.LABEL_MAX, message = "태그는 50자까지 입력할 수 있어요")
        String tag,
        // memo.color 는 varchar(7) — "#RRGGBB" 한 벌만 들어간다. 길이만 재던 종전엔
        // "zzz" 가 그대로 저장돼 메모 색이 안 칠해졌다(QA 2026-09-07 #86).
        @Pattern(regexp = ColorFormat.HEX_RGB, message = ColorFormat.MESSAGE)
        String color
    ) {}

    /**
     * 수정 본문 — <b>실린 칸만 바꾼다</b>(QA #96, 사용자 결정 2026-09-07).
     *
     * <p>{@code Optional} 인 이유는 "안 보냈다" 와 {@code "content": null} 을 갈라야 해서다.
     * 참조가 {@code null} 이면 키가 없었던 것이고({@code AbsentAwareOptionalModule}),
     * {@code Optional.empty()} 면 지우라는 뜻이다. 제약은 <b>실린 값에만</b> 걸린다 —
     * 안 보낸 제목은 검사도 안 하고, {@code "title": null} 은 종전처럼 400 이다.
     */
    @Schema(name = "MemoUpdateRequest")
    public record UpdateRequest(
        Optional<@NotBlank(message = "메모 제목을 입력해 주세요")
                 @Size(max = FieldLimits.TITLE_MAX, message = "제목은 200자까지 입력할 수 있어요")
                 String> title,
        Optional<@Size(max = FieldLimits.CONTENT_MAX, message = "본문은 10,000자까지 입력할 수 있어요")
                 String> content,
        Optional<@Size(max = FieldLimits.LABEL_MAX, message = "태그는 50자까지 입력할 수 있어요")
                 String> tag,
        // memo.color 는 varchar(7) — "#RRGGBB" 한 벌만 들어간다. 길이만 재던 종전엔
        // "zzz" 가 그대로 저장돼 메모 색이 안 칠해졌다(QA 2026-09-07 #86).
        Optional<@Pattern(regexp = ColorFormat.HEX_RGB, message = ColorFormat.MESSAGE)
                 String> color
    ) {}

    @Schema(name = "MemoResponse")
    public record Response(
        Long rowId,
        Long userRowId,
        String title,
        String content,
        String tag,
        String color,
        YNType isPinned,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(MemoServiceDto.MemoInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.title(),
                info.content(),
                info.tag(),
                info.color(),
                info.isPinned(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    @Schema(name = "MemoListResponse")
    public record ListResponse(
        List<Response> memos
    ) {
        public static ListResponse from(List<MemoServiceDto.MemoInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }
}
