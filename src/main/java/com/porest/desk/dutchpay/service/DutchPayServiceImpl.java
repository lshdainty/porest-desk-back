package com.porest.desk.dutchpay.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.dutchpay.domain.DutchPay;
import com.porest.desk.dutchpay.domain.DutchPayParticipant;
import com.porest.desk.dutchpay.repository.DutchPayRepository;
import com.porest.desk.dutchpay.service.dto.DutchPayServiceDto;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseRepository;
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
public class DutchPayServiceImpl implements DutchPayService {
    private final DutchPayRepository dutchPayRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;

    @Override
    @Transactional
    public DutchPayServiceDto.DutchPayInfo createDutchPay(DutchPayServiceDto.CreateCommand command) {
        log.debug("더치페이 생성 시작: userRowId={}, title={}", command.userRowId(), command.title());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        Expense sourceExpense = resolveSourceExpense(command.sourceExpenseRowId(), command.userRowId());

        DutchPay dutchPay = DutchPay.createDutchPay(
            user,
            sourceExpense,
            command.title(),
            command.description(),
            command.totalAmount(),
            command.currency() != null ? command.currency() : "KRW",
            command.splitMethod(),
            command.dutchPayDate()
        );

        addParticipants(dutchPay, command.participants());

        dutchPayRepository.save(dutchPay);
        log.info("더치페이 생성 완료: dutchPayId={}", dutchPay.getRowId());

        return DutchPayServiceDto.DutchPayInfo.from(dutchPay);
    }

    @Override
    public List<DutchPayServiceDto.DutchPayInfo> getDutchPays(Long userRowId) {
        log.debug("더치페이 목록 조회: userRowId={}", userRowId);

        List<DutchPay> dutchPays = dutchPayRepository.findAllByUser(userRowId);

        return dutchPays.stream()
            .map(DutchPayServiceDto.DutchPayInfo::from)
            .toList();
    }

    @Override
    public DutchPayServiceDto.DutchPayInfo getDutchPay(Long dutchPayId, Long userRowId) {
        log.debug("더치페이 상세 조회: dutchPayId={}", dutchPayId);

        DutchPay dutchPay = findDutchPayOrThrow(dutchPayId);
        validateDutchPayOwnership(dutchPay, userRowId);

        return DutchPayServiceDto.DutchPayInfo.from(dutchPay);
    }

    @Override
    @Transactional
    public DutchPayServiceDto.DutchPayInfo updateDutchPay(Long dutchPayId, Long userRowId, DutchPayServiceDto.UpdateCommand command) {
        log.debug("더치페이 수정 시작: dutchPayId={}", dutchPayId);

        DutchPay dutchPay = findDutchPayOrThrow(dutchPayId);
        validateDutchPayOwnership(dutchPay, userRowId);

        dutchPay.updateDutchPay(
            command.title(),
            command.description(),
            command.totalAmount(),
            command.currency() != null ? command.currency() : "KRW",
            command.splitMethod(),
            command.dutchPayDate()
        );

        dutchPay.clearParticipants();
        addParticipants(dutchPay, command.participants());

        dutchPayRepository.save(dutchPay);
        log.info("더치페이 수정 완료: dutchPayId={}", dutchPayId);

        return DutchPayServiceDto.DutchPayInfo.from(dutchPay);
    }

    @Override
    @Transactional
    public void deleteDutchPay(Long dutchPayId, Long userRowId) {
        log.debug("더치페이 삭제 시작: dutchPayId={}", dutchPayId);

        DutchPay dutchPay = findDutchPayOrThrow(dutchPayId);
        validateDutchPayOwnership(dutchPay, userRowId);

        dutchPay.deleteDutchPay();
        dutchPayRepository.save(dutchPay);

        log.info("더치페이 삭제 완료: dutchPayId={}", dutchPayId);
    }

    @Override
    @Transactional
    public DutchPayServiceDto.DutchPayInfo markParticipantPaid(Long dutchPayId, Long userRowId, Long participantId) {
        log.debug("참가자 정산 처리: dutchPayId={}, participantId={}", dutchPayId, participantId);

        DutchPay dutchPay = findDutchPayOrThrow(dutchPayId);
        validateDutchPayOwnership(dutchPay, userRowId);

        DutchPayParticipant participant = dutchPay.getParticipants().stream()
            .filter(p -> p.getRowId().equals(participantId))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.DUTCH_PAY_PARTICIPANT_NOT_FOUND));

        participant.markPaid();
        dutchPay.checkSettled();
        dutchPayRepository.save(dutchPay);

        log.info("참가자 정산 완료: participantId={}", participantId);

        return DutchPayServiceDto.DutchPayInfo.from(dutchPay);
    }

    @Override
    @Transactional
    public DutchPayServiceDto.DutchPayInfo settleAll(Long dutchPayId, Long userRowId) {
        log.debug("더치페이 전체 정산: dutchPayId={}", dutchPayId);

        DutchPay dutchPay = findDutchPayOrThrow(dutchPayId);
        validateDutchPayOwnership(dutchPay, userRowId);

        dutchPay.settleAll();
        dutchPayRepository.save(dutchPay);

        log.info("더치페이 전체 정산 완료: dutchPayId={}", dutchPayId);

        return DutchPayServiceDto.DutchPayInfo.from(dutchPay);
    }

    private Expense resolveSourceExpense(Long sourceExpenseRowId, Long userRowId) {
        if (sourceExpenseRowId == null) return null;
        Expense expense = expenseRepository.findById(sourceExpenseRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_NOT_FOUND));
        if (!expense.getUser().getRowId().equals(userRowId)) {
            throw new ForbiddenException(DeskErrorCode.EXPENSE_ACCESS_DENIED);
        }
        return expense;
    }

    private void addParticipants(DutchPay dutchPay, List<DutchPayServiceDto.ParticipantCommand> participants) {
        if (participants == null) return;
        for (DutchPayServiceDto.ParticipantCommand pc : participants) {
            User participantUser = null;
            if (pc.userRowId() != null) {
                participantUser = userRepository.findById(pc.userRowId())
                    .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
            }
            DutchPayParticipant participant = DutchPayParticipant.create(
                dutchPay, participantUser, pc.participantName(), pc.amount()
            );
            dutchPay.addParticipant(participant);
        }
    }

    private void validateDutchPayOwnership(DutchPay dutchPay, Long userRowId) {
        if (!dutchPay.getUser().getRowId().equals(userRowId)) {
            log.warn("더치페이 소유권 검증 실패 - dutchPayId={}, ownerRowId={}, requestUserRowId={}",
                dutchPay.getRowId(), dutchPay.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.DUTCHPAY_ACCESS_DENIED);
        }
    }

    private DutchPay findDutchPayOrThrow(Long dutchPayId) {
        return dutchPayRepository.findById(dutchPayId)
            .orElseThrow(() -> {
                log.warn("더치페이 조회 실패 - 존재하지 않는 더치페이: dutchPayId={}", dutchPayId);
                return new EntityNotFoundException(DeskErrorCode.DUTCH_PAY_NOT_FOUND);
            });
    }
}
