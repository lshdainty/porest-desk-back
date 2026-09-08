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
        // 태그 마스터를 직접 고른 경우 — 실으면 tag 문자열보다 이 값이 이긴다(MemoTagApiController
        // 로 만든 태그를 화면에서 고르는 경로). 안 실으면 서버가 tag 문자열로 마스터를 확보한다.
        // 남의 태그 아이디는 403 이다.
        Long memoTagRowId,
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
        /*
         * 태그 마스터 아이디 — 실렸으면 tag 문자열보다 이 값이 이긴다.
         * 키가 없으면 태그를 안 고친다는 뜻이라 tag 문자열(병합된 값)로 잇고,
         * "memoTagRowId": null 은 태그를 뗀다는 뜻이라 tag 문자열까지 함께 비운다 —
         * 문자열만 남기면 다음 저장에서 그 이름의 태그가 되살아난다(QA #88).
         */
        Optional<Long> memoTagRowId,
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
        /*
         * 이 메모에 붙은 태그 마스터의 아이디 — 태그가 없으면 null.
         *
         * 이름(tag)이 아니라 아이디를 싣는다: 이름은 사용자가 바꿀 수 있는 속성이라 개명하는
         * 순간 화면이 들고 있던 값과 갈린다. 색·정렬 같은 나머지는 GET /memo-tags 가 주므로
         * 여기서는 어느 행인지만 가리키면 된다.
         */
        Long memoTagRowId,
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
                info.memoTagRowId(),
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
