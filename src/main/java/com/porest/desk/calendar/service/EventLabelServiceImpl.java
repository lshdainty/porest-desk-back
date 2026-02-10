package com.porest.desk.calendar.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.calendar.domain.EventLabel;
import com.porest.desk.calendar.repository.EventLabelRepository;
import com.porest.desk.calendar.service.dto.EventLabelServiceDto;
import com.porest.desk.common.exception.DeskErrorCode;
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
public class EventLabelServiceImpl implements EventLabelService {
    private final EventLabelRepository eventLabelRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public EventLabelServiceDto.LabelInfo createLabel(EventLabelServiceDto.CreateCommand command) {
        log.debug("일정 라벨 생성 시작: userRowId={}", command.userRowId());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        List<EventLabel> existing = eventLabelRepository.findAllByUser(command.userRowId());
        int nextOrder = existing.size();

        EventLabel label = EventLabel.createLabel(user, command.labelName(), command.color(), nextOrder);
        eventLabelRepository.save(label);

        log.info("일정 라벨 생성 완료: labelId={}", label.getRowId());
        return EventLabelServiceDto.LabelInfo.from(label);
    }

    @Override
    public List<EventLabelServiceDto.LabelInfo> getLabels(Long userRowId) {
        log.debug("일정 라벨 목록 조회: userRowId={}", userRowId);
        return eventLabelRepository.findAllByUser(userRowId).stream()
            .map(EventLabelServiceDto.LabelInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public EventLabelServiceDto.LabelInfo updateLabel(Long labelId, EventLabelServiceDto.UpdateCommand command) {
        log.debug("일정 라벨 수정 시작: labelId={}", labelId);

        EventLabel label = eventLabelRepository.findById(labelId)
            .orElseThrow(() -> {
                log.warn("일정 라벨 조회 실패: labelId={}", labelId);
                return new EntityNotFoundException(DeskErrorCode.EVENT_LABEL_NOT_FOUND);
            });

        label.updateLabel(command.labelName(), command.color());
        log.info("일정 라벨 수정 완료: labelId={}", labelId);
        return EventLabelServiceDto.LabelInfo.from(label);
    }

    @Override
    @Transactional
    public void deleteLabel(Long labelId) {
        log.debug("일정 라벨 삭제 시작: labelId={}", labelId);

        EventLabel label = eventLabelRepository.findById(labelId)
            .orElseThrow(() -> {
                log.warn("일정 라벨 조회 실패: labelId={}", labelId);
                return new EntityNotFoundException(DeskErrorCode.EVENT_LABEL_NOT_FOUND);
            });

        label.deleteLabel();
        log.info("일정 라벨 삭제 완료: labelId={}", labelId);
    }
}
