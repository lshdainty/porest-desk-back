package com.porest.desk.memo.service;

import com.porest.desk.memo.service.dto.MemoTagServiceDto;

import java.util.List;

public interface MemoTagService {
    MemoTagServiceDto.TagInfo createTag(MemoTagServiceDto.CreateCommand command);
    List<MemoTagServiceDto.TagInfo> getTags(Long userRowId);
    MemoTagServiceDto.TagInfo updateTag(Long tagId, Long userRowId, MemoTagServiceDto.UpdateCommand command);
    void deleteTag(Long tagId, Long userRowId);

    /**
     * 이름으로 그 사용자의 활성 태그를 확보한다 — 없으면 만든다. 메모 저장이 {@code tag}
     * 문자열을 마스터로 잇는 자리다({@code MemoServiceImpl}).
     *
     * <p>{@link #createTag} 로는 안 되는 이유는 답이 다르기 때문이다 — 등록은 "그 이름은 이미
     * 있다" 를 409 로 알려야 하지만, 여기서 같은 이름을 만나는 것은 <b>정상이고 원하는 결과</b>다.
     *
     * <p>엔티티가 아니라 {@code rowId} 를 돌려준다 — 확보는 <b>새 트랜잭션</b>에서 돌므로
     * 거기서 만든 엔티티는 부르는 쪽 영속성 컨텍스트에 속하지 않는다(할 일 태그와 같은 이유).
     */
    Long findOrCreateByName(Long userRowId, String rawTagName);
}
