package com.porest.desk.dutchpay.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
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
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;

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

        syncParticipants(dutchPay, command.participants());

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

        DutchPayParticipant participant = dutchPay.getActiveParticipants().stream()
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

    /**
     * 참가자 목록을 rowId 로 맞춰 간다 — 있으면 제자리 수정, 없으면 신규, 안 온 건 삭제.
     *
     * <p>통째로 지우고 새로 만들면 <b>정산 완료 표시(is_paid/paid_at)가 전부 풀린다.</b>
     * 3명이 이미 입금해 체크해 뒀는데 금액 한 줄 고쳤다고 그게 날아가면 안 된다.
     */
    private void syncParticipants(DutchPay dutchPay, List<DutchPayServiceDto.ParticipantCommand> participants) {
        if (participants == null) {
            return;
        }
        validateNoDuplicateParticipants(participants);
        List<DutchPayParticipant> existing = List.copyOf(dutchPay.getActiveParticipants());
        Map<Long, DutchPayParticipant> byId = existing.stream()
            .filter(pt -> pt.getRowId() != null)
            .collect(Collectors.toMap(DutchPayParticipant::getRowId, pt -> pt, (a, b) -> a));

        Set<DutchPayParticipant> kept =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (DutchPayServiceDto.ParticipantCommand pc : participants) {
            if (pc.amount() == null || pc.amount() <= 0) {
                throw new InvalidValueException(DeskErrorCode.DUTCH_PAY_INVALID_PARTICIPANT_AMOUNT);
            }
            User participantUser = null;
            if (pc.userRowId() != null) {
                participantUser = userRepository.findById(pc.userRowId())
                    .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
            }
            DutchPayParticipant found = pc.rowId() != null ? byId.get(pc.rowId()) : null;
            if (found != null) {
                found.updateParticipant(participantUser, pc.participantName(), pc.amount());
                kept.add(found);
            } else {
                dutchPay.addParticipant(
                    DutchPayParticipant.create(dutchPay, participantUser, pc.participantName(), pc.amount()));
            }
        }
        // 목록에서 빠진 참가자만 지운다. id 로 매칭되지 않은 것도 여기 걸린다.
        for (DutchPayParticipant pt : existing) {
            if (!kept.contains(pt)) {
                pt.deleteParticipant();
            }
        }
    }

    private void addParticipants(DutchPay dutchPay, List<DutchPayServiceDto.ParticipantCommand> participants) {
        if (participants == null) return;
        validateNoDuplicateParticipants(participants);
        for (DutchPayServiceDto.ParticipantCommand pc : participants) {
            // amount 는 not-null 컬럼 — null/0/음수는 정산 데이터를 오염시키므로 영속화 전에 차단.
            if (pc.amount() == null || pc.amount() <= 0) {
                throw new InvalidValueException(DeskErrorCode.DUTCH_PAY_INVALID_PARTICIPANT_AMOUNT);
            }
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

    /**
     * 한 더치페이 내 참가자 중복 금지. 수정 시 전체 교체(clearParticipants→재추가) 구조라
     * 활성 참가자 집합은 곧 이 요청의 목록이므로 요청 내 중복만 막으면 충분하다.
     * 등록 사용자(user_row_id)는 사용자 기준, 이름만 있는 참가자(user=null)는 이름 기준으로 판정.
     */
    private void validateNoDuplicateParticipants(List<DutchPayServiceDto.ParticipantCommand> participants) {
        java.util.Set<Long> seenUserIds = new java.util.HashSet<>();
        java.util.Set<String> seenNamesNoUser = new java.util.HashSet<>();
        for (DutchPayServiceDto.ParticipantCommand pc : participants) {
            if (pc.userRowId() != null) {
                if (!seenUserIds.add(pc.userRowId())) {
                    throw new InvalidValueException(DeskErrorCode.DUTCH_PAY_DUPLICATE_PARTICIPANT);
                }
            } else if (pc.participantName() != null) {
                if (!seenNamesNoUser.add(pc.participantName())) {
                    throw new InvalidValueException(DeskErrorCode.DUTCH_PAY_DUPLICATE_PARTICIPANT);
                }
            }
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
