package com.porest.desk.calculator.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.calculator.domain.CalculatorHistory;
import com.porest.desk.calculator.repository.CalculatorHistoryRepository;
import com.porest.desk.calculator.service.dto.CalculatorHistoryServiceDto;
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
public class CalculatorHistoryServiceImpl implements CalculatorHistoryService {
    private final CalculatorHistoryRepository calculatorHistoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CalculatorHistoryServiceDto.HistoryInfo createHistory(CalculatorHistoryServiceDto.CreateCommand command) {
        log.debug("계산기 기록 등록 시작: userRowId={}, expression={}", command.userRowId(), command.expression());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        CalculatorHistory history = CalculatorHistory.createHistory(
            user,
            command.expression(),
            command.result()
        );

        calculatorHistoryRepository.save(history);
        log.info("계산기 기록 등록 완료: historyId={}, userRowId={}", history.getRowId(), command.userRowId());

        return CalculatorHistoryServiceDto.HistoryInfo.from(history);
    }

    @Override
    public List<CalculatorHistoryServiceDto.HistoryInfo> getHistories(Long userRowId) {
        log.debug("계산기 기록 목록 조회: userRowId={}", userRowId);

        List<CalculatorHistory> histories = calculatorHistoryRepository.findAllByUser(userRowId);

        return histories.stream()
            .map(CalculatorHistoryServiceDto.HistoryInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public void deleteAllHistories(Long userRowId) {
        log.debug("계산기 기록 전체 삭제 시작: userRowId={}", userRowId);

        calculatorHistoryRepository.deleteAllByUser(userRowId);

        log.info("계산기 기록 전체 삭제 완료: userRowId={}", userRowId);
    }
}
