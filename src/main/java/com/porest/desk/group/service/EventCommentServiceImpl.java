package com.porest.desk.group.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.group.domain.EventComment;
import com.porest.desk.group.repository.EventCommentRepository;
import com.porest.desk.group.service.dto.EventCommentServiceDto;
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

    @Override
    @Transactional
    public EventCommentServiceDto.CommentInfo createComment(EventCommentServiceDto.CreateCommand command) {
        log.debug("일정 댓글 생성: eventRowId={}, userRowId={}", command.eventRowId(), command.userRowId());

        CalendarEvent event = calendarEventRepository.findById(command.eventRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.CALENDAR_EVENT_NOT_FOUND));

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        EventComment parent = null;
        if (command.parentRowId() != null) {
            parent = eventCommentRepository.findById(command.parentRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EVENT_COMMENT_NOT_FOUND));
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
    public EventCommentServiceDto.CommentInfo updateComment(EventCommentServiceDto.UpdateCommand command) {
        log.debug("일정 댓글 수정: commentRowId={}", command.commentRowId());

        EventComment comment = eventCommentRepository.findById(command.commentRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EVENT_COMMENT_NOT_FOUND));

        comment.updateContent(command.content());
        eventCommentRepository.save(comment);

        log.info("일정 댓글 수정 완료: commentId={}", comment.getRowId());
        return EventCommentServiceDto.CommentInfo.from(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentRowId) {
        log.debug("일정 댓글 삭제: commentRowId={}", commentRowId);

        EventComment comment = eventCommentRepository.findById(commentRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EVENT_COMMENT_NOT_FOUND));

        comment.deleteComment();
        eventCommentRepository.save(comment);
        log.info("일정 댓글 삭제 완료: commentId={}", commentRowId);
    }
}
