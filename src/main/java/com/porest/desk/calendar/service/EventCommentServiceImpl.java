package com.porest.desk.calendar.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.calendar.domain.EventComment;
import com.porest.desk.calendar.repository.EventCommentRepository;
import com.porest.desk.calendar.service.dto.EventCommentServiceDto;
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
public class EventCommentServiceImpl implements EventCommentService {
    private final EventCommentRepository eventCommentRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;
    private final CalendarMembershipValidator calendarMembershipValidator;

    @Override
    @Transactional
    public EventCommentServiceDto.CommentInfo createComment(EventCommentServiceDto.CreateCommand command) {
        log.debug("일정 댓글 생성: eventRowId={}, userRowId={}", command.eventRowId(), command.userRowId());

        CalendarEvent event = calendarEventRepository.findById(command.eventRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.CALENDAR_EVENT_NOT_FOUND));

        // 접근 권한 검증: 이벤트가 속한 캘린더 멤버(읽기 이상)만 댓글 작성 가능.
        // 접근조차 불가한 이벤트에 댓글을 다는 것을 차단(공유 캘린더면 멤버십으로 판정).
        if (event.getCalendar() != null) {
            calendarMembershipValidator.validateMembership(event.getCalendar().getRowId(), command.userRowId());
        }

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        EventComment parent = null;
        if (command.parentRowId() != null) {
            parent = eventCommentRepository.findById(command.parentRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EVENT_COMMENT_NOT_FOUND));
            // 무결성: 부모 댓글은 반드시 같은 이벤트 소속이어야 함(타 이벤트 댓글을 부모로 지정해 스레드 오염 차단).
            Long parentEventRowId = parent.getEvent() != null ? parent.getEvent().getRowId() : null;
            if (!event.getRowId().equals(parentEventRowId)) {
                throw new InvalidValueException(DeskErrorCode.EVENT_COMMENT_NOT_FOUND);
            }
        }

        EventComment comment = EventComment.create(event, user, parent, command.content());
        eventCommentRepository.save(comment);

        log.info("일정 댓글 생성 완료: commentId={}", comment.getRowId());
        return EventCommentServiceDto.CommentInfo.from(comment);
    }

    @Override
    public List<EventCommentServiceDto.CommentInfo> getComments(Long eventRowId) {
        log.debug("일정 댓글 목록 조회: eventRowId={}", eventRowId);

        return eventCommentRepository.findAllByEvent(eventRowId).stream()
            .map(EventCommentServiceDto.CommentInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public EventCommentServiceDto.CommentInfo updateComment(Long userRowId, EventCommentServiceDto.UpdateCommand command) {
        log.debug("일정 댓글 수정: commentRowId={}", command.commentRowId());

        EventComment comment = eventCommentRepository.findById(command.commentRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EVENT_COMMENT_NOT_FOUND));
        validateCommentOwnership(comment, userRowId);

        comment.updateContent(command.content());
        eventCommentRepository.save(comment);

        log.info("일정 댓글 수정 완료: commentId={}", comment.getRowId());
        return EventCommentServiceDto.CommentInfo.from(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentRowId, Long userRowId) {
        log.debug("일정 댓글 삭제: commentRowId={}", commentRowId);

        EventComment comment = eventCommentRepository.findById(commentRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EVENT_COMMENT_NOT_FOUND));
        validateCommentOwnership(comment, userRowId);

        comment.deleteComment();
        eventCommentRepository.save(comment);
        log.info("일정 댓글 삭제 완료: commentId={}", commentRowId);
    }

    private void validateCommentOwnership(EventComment comment, Long userRowId) {
        if (!comment.getUser().getRowId().equals(userRowId)) {
            log.warn("일정 댓글 소유권 검증 실패 - commentId={}, ownerRowId={}, requestUserRowId={}",
                comment.getRowId(), comment.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.EVENT_COMMENT_ACCESS_DENIED);
        }
    }
}
