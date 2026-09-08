package com.porest.desk.memo.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.common.patch.Patch;
import com.porest.desk.memo.domain.Memo;

import java.time.LocalDateTime;

public class MemoServiceDto {

    public record CreateCommand(
        Long userRowId,
        String title,
        String content,
        String tag,
        Long memoTagRowId,
        String color
    ) {}

    /** 수정 명령 — 각 칸은 "안 왔다 / 지워라 / 이 값으로" 셋 중 하나다({@link Patch}). */
    public record UpdateCommand(
        Patch<String> title,
        Patch<String> content,
        Patch<String> tag,
        Patch<Long> memoTagRowId,
        Patch<String> color
    ) {}

    public record MemoInfo(
        Long rowId,
        Long userRowId,
        String title,
        String content,
        String tag,
        Long memoTagRowId,
        String color,
        YNType isPinned,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static MemoInfo from(Memo memo) {
            return new MemoInfo(
                memo.getRowId(),
                memo.getUser().getRowId(),
                memo.getTitle(),
                memo.getContent(),
                memo.getTag(),
                memo.getMemoTag() == null ? null : memo.getMemoTag().getRowId(),
                memo.getColor(),
                memo.getIsPinned(),
                memo.getCreateAt(),
                memo.getModifyAt()
            );
        }
    }
}
