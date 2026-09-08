package com.porest.desk.memo.controller.dto;

import com.porest.desk.common.validation.ColorFormat;
import com.porest.desk.memo.service.dto.MemoTagServiceDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.List;

public class MemoTagApiDto {

    @Schema(name = "MemoTagCreateRequest")
    public record CreateRequest(
        String tagName,
        @Pattern(regexp = ColorFormat.HEX_RGB, message = ColorFormat.MESSAGE)
        String color
    ) {}

    @Schema(name = "MemoTagUpdateRequest")
    public record UpdateRequest(
        String tagName,
        @Pattern(regexp = ColorFormat.HEX_RGB, message = ColorFormat.MESSAGE)
        String color
    ) {}

    @Schema(name = "MemoTagResponse")
    public record Response(
        Long rowId,
        Long userRowId,
        String tagName,
        String color,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        long usageCount
    ) {
        public static Response from(MemoTagServiceDto.TagInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.tagName(),
                info.color(),
                info.createAt(),
                info.modifyAt(),
                info.usageCount()
            );
        }
    }

    @Schema(name = "MemoTagListResponse")
    public record ListResponse(
        List<Response> tags
    ) {
        public static ListResponse from(List<MemoTagServiceDto.TagInfo> infos) {
            return new ListResponse(infos.stream().map(Response::from).toList());
        }
    }
}
