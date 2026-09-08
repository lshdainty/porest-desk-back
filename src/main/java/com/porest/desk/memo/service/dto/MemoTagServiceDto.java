package com.porest.desk.memo.service.dto;

import com.porest.desk.memo.domain.MemoTag;

import java.time.LocalDateTime;

public class MemoTagServiceDto {

    public record CreateCommand(
        Long userRowId,
        String tagName,
        String color
    ) {}

    public record UpdateCommand(
        String tagName,
        String color
    ) {}

    /**
     * 확보된 마스터의 요약 — {@code findOrCreateByName} 이 <b>엔티티 대신</b> 돌려주는 값이다.
     *
     * <p>확보는 새 트랜잭션에서 돌고, 부르는 트랜잭션은 그 커밋 전에 이미 스냅샷을 잡아 둔
     * 상태라 그 행을 <b>다시 읽을 수 없다</b>(MariaDB REPEATABLE READ · #310 실측).
     * 그래서 부르는 쪽은 FK 를 프록시({@code getReference})로 잇는데, 프록시에서 이름을 읽으면
     * 거기서 지연 로딩 SELECT 가 나가 같은 이유로 터진다({@code EntityNotFoundException}).
     * 확보가 <b>보이는 자리에서</b> 읽어 둔 값을 여기 실어 보내는 이유다 — 부르는 쪽은
     * 프록시를 한 번도 건드리지 않고 표기를 통일하고 응답을 만든다.
     */
    public record TagRef(
        Long rowId,
        String tagName,
        String color
    ) {
        public static TagRef from(MemoTag tag) {
            return new TagRef(tag.getRowId(), tag.getTagName(), tag.getColor());
        }
    }

    public record TagInfo(
        Long rowId,
        Long userRowId,
        String tagName,
        String color,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        long usageCount
    ) {
        public static TagInfo from(MemoTag tag) {
            return from(tag, 0L);
        }

        public static TagInfo from(MemoTag tag, long usageCount) {
            return new TagInfo(
                tag.getRowId(),
                tag.getUser().getRowId(),
                tag.getTagName(),
                tag.getColor(),
                tag.getCreateAt(),
                tag.getModifyAt(),
                usageCount
            );
        }
    }
}
