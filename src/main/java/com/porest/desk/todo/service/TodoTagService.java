package com.porest.desk.todo.service;

import com.porest.desk.todo.service.dto.TodoTagServiceDto;

import java.util.List;

public interface TodoTagService {
    TodoTagServiceDto.TagInfo createTag(TodoTagServiceDto.CreateCommand command);
    List<TodoTagServiceDto.TagInfo> getTags(Long userRowId);
    TodoTagServiceDto.TagInfo updateTag(Long tagId, Long userRowId, TodoTagServiceDto.UpdateCommand command);
    void deleteTag(Long tagId, Long userRowId);

    /**
     * 이름으로 그 사용자의 활성 태그를 확보한다 — 없으면 만든다. 할 일 저장이 {@code category}
     * 문자열을 태그로 잇는 자리다({@code TodoServiceImpl}).
     *
     * <p>{@link #createTag} 로는 안 되는 이유는 답이 다르기 때문이다 — 등록은 "그 이름은 이미
     * 있다" 를 409 로 알려야 하지만, 여기서 같은 이름을 만나는 것은 <b>정상이고 원하는 결과</b>다.
     *
     * <p>엔티티가 아니라 {@link TodoTagServiceDto.TagRef} 를 돌려준다 — 확보는 <b>새 트랜잭션</b>에서
     * 돌므로 거기서 만든 엔티티는 부르는 쪽 영속성 컨텍스트에 속하지 않는다. 그렇다고
     * {@code rowId} 만 주면 부르는 쪽이 이름·색을 읽으려고 그 행을 다시 조회하게 되는데, 방금
     * 커밋된 행은 부르는 트랜잭션의 스냅샷에 없어 <b>안 보인다</b>(QA #102). 그래서
     * <b>보이는 자리에서</b> 읽은 이름·색을 함께 실어 보낸다.
     */
    TodoTagServiceDto.TagRef findOrCreateByName(Long userRowId, String rawTagName);
}
