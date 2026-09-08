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
        /**
         * 조회 경로용 — 붙은 마스터의 아이디를 <b>메모에서 읽어</b> 싣는다.
         *
         * <p>여기서 {@code getMemoTag()} 를 건드려도 되는 이유는 조회가 태그를 함께 끌고
         * 오기 때문이다({@code MemoQueryDslRepository} 의 {@code leftJoin ... fetchJoin}).
         * <b>저장 경로에서는 쓰지 마라</b> — 거기서 붙는 것은 조회를 안 한 프록시라
         * 아이디를 읽는 것만으로 SELECT 가 나가고, 방금 새 트랜잭션이 만든 태그는 그
         * SELECT 에 안 잡혀 터진다(QA #102). 저장은 아래 {@code from(memo, memoTagRowId)} 를 쓴다.
         */
        public static MemoInfo from(Memo memo) {
            return from(memo, memo.getMemoTag() == null ? null : memo.getMemoTag().getRowId());
        }

        /** 저장 경로용 — 이번 저장이 <b>이미 알고 있는</b> 마스터 아이디를 그대로 싣는다(프록시를 안 건드린다). */
        public static MemoInfo from(Memo memo, Long memoTagRowId) {
            return new MemoInfo(
                memo.getRowId(),
                memo.getUser().getRowId(),
                memo.getTitle(),
                memo.getContent(),
                memo.getTag(),
                memoTagRowId,
                memo.getColor(),
                memo.getIsPinned(),
                memo.getCreateAt(),
                memo.getModifyAt()
            );
        }
    }
}
