package com.porest.desk.todo.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.common.util.NameNormalizer;
import com.porest.desk.common.validation.FieldLimits;
import com.porest.desk.todo.domain.TodoTag;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.repository.TodoTagRepository;
import com.porest.desk.todo.service.dto.TodoTagServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TodoTagServiceImpl implements TodoTagService {
    private final TodoTagRepository todoTagRepository;
    private final TodoRepository todoRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TodoTagServiceDto.TagInfo createTag(TodoTagServiceDto.CreateCommand command) {
        log.debug("태그 등록 시작: userRowId={}, tagName={}", command.userRowId(), command.tagName());

        // 저장 전에 이름을 한 번 다듬는다 — 검사와 저장이 같은 값을 보게 만드는 자리다.
        String tagName = NameNormalizer.require(command.tagName(), FieldLimits.NAME_MAX);

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        // 활성(미삭제) 태그 중 동일 이름 중복 금지 (soft-delete 된 같은 이름은 재사용 허용)
        if (todoTagRepository.existsActiveByUserAndName(command.userRowId(), tagName, null)) {
            throw new InvalidValueException(DeskErrorCode.TODO_TAG_DUPLICATE_NAME);
        }

        TodoTag tag = TodoTag.createTag(user, tagName, command.color());
        todoTagRepository.save(tag);
        flushOrRejectDuplicate();

        log.info("태그 등록 완료: tagId={}", tag.getRowId());

        return TodoTagServiceDto.TagInfo.from(tag);
    }

    @Override
    public List<TodoTagServiceDto.TagInfo> getTags(Long userRowId) {
        log.debug("태그 목록 조회: userRowId={}", userRowId);

        // 태그별 사용 할일 수 — category(태그명) GROUP BY 1회 집계(N+1 금지).
        Map<String, Long> usage = todoRepository.countByCategory(userRowId);
        return todoTagRepository.findAllByUser(userRowId).stream()
            .map(tag -> TodoTagServiceDto.TagInfo.from(
                tag, usage.getOrDefault(tag.getTagName(), 0L)))
            .toList();
    }

    @Override
    @Transactional
    public TodoTagServiceDto.TagInfo updateTag(Long tagId, Long userRowId, TodoTagServiceDto.UpdateCommand command) {
        log.debug("태그 수정 시작: tagId={}", tagId);

        TodoTag tag = findTagOrThrow(tagId);
        validateTagOwnership(tag, userRowId);

        String tagName = NameNormalizer.require(command.tagName(), FieldLimits.NAME_MAX);
        // 이름 변경 시 자기 자신을 제외한 활성 태그와 중복 금지
        if (todoTagRepository.existsActiveByUserAndName(userRowId, tagName, tagId)) {
            throw new InvalidValueException(DeskErrorCode.TODO_TAG_DUPLICATE_NAME);
        }
        tag.updateTag(tagName, command.color());
        flushOrRejectDuplicate();

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

    /**
     * 조회 검사를 빠져나간 동시 저장 경쟁을 409 로 받는다 — 라벨과 같은 이유·같은 모양이다
     * (EventLabelServiceImpl.flushOrRejectDuplicate 주석에 전말을 적어 뒀다).
     */
    private void flushOrRejectDuplicate() {
        try {
            todoTagRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new InvalidValueException(DeskErrorCode.TODO_TAG_DUPLICATE_NAME, e);
        }
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
