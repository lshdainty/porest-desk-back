package com.porest.desk.todo.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.todo.domain.TodoTag;
import com.porest.desk.todo.repository.TodoTagRepository;
import com.porest.desk.todo.service.dto.TodoTagServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TodoTagServiceImpl implements TodoTagService {
    private final TodoTagRepository todoTagRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TodoTagServiceDto.TagInfo createTag(TodoTagServiceDto.CreateCommand command) {
        log.debug("태그 등록 시작: userRowId={}, tagName={}", command.userRowId(), command.tagName());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        TodoTag tag = TodoTag.createTag(user, command.tagName(), command.color());
        todoTagRepository.save(tag);

        log.info("태그 등록 완료: tagId={}", tag.getRowId());

        return TodoTagServiceDto.TagInfo.from(tag);
    }

    @Override
    public List<TodoTagServiceDto.TagInfo> getTags(Long userRowId) {
        log.debug("태그 목록 조회: userRowId={}", userRowId);

        return todoTagRepository.findAllByUser(userRowId).stream()
            .map(TodoTagServiceDto.TagInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public TodoTagServiceDto.TagInfo updateTag(Long tagId, Long userRowId, TodoTagServiceDto.UpdateCommand command) {
        log.debug("태그 수정 시작: tagId={}", tagId);

        TodoTag tag = findTagOrThrow(tagId);
        validateTagOwnership(tag, userRowId);
        tag.updateTag(command.tagName(), command.color());

        log.info("태그 수정 완료: tagId={}", tagId);

        return TodoTagServiceDto.TagInfo.from(tag);
    }

    @Override
    @Transactional
    public void deleteTag(Long tagId, Long userRowId) {
        log.debug("태그 삭제 시작: tagId={}", tagId);

        TodoTag tag = findTagOrThrow(tagId);
        validateTagOwnership(tag, userRowId);
        tag.deleteTag();

        log.info("태그 삭제 완료: tagId={}", tagId);
    }

    private void validateTagOwnership(TodoTag tag, Long userRowId) {
        if (!tag.getUser().getRowId().equals(userRowId)) {
            log.warn("태그 소유권 검증 실패 - tagId={}, ownerRowId={}, requestUserRowId={}",
                tag.getRowId(), tag.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.TODO_ACCESS_DENIED);
        }
    }

    private TodoTag findTagOrThrow(Long tagId) {
        return todoTagRepository.findById(tagId)
            .orElseThrow(() -> {
                log.warn("태그 조회 실패 - 존재하지 않는 태그: tagId={}", tagId);
                return new EntityNotFoundException(DeskErrorCode.TODO_TAG_NOT_FOUND);
            });
    }
}
