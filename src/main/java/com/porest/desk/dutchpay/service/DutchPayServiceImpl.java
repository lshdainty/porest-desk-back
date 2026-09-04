package com.porest.desk.dutchpay.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.common.util.NameNormalizer;
import com.porest.desk.common.validation.FieldLimits;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        flushOrRejectDuplicate();
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
        List<String> names = validateNoDuplicateParticipants(participants);
        int payerIndex = resolvePayerIndex(participants);
        List<DutchPayParticipant> existing = List.copyOf(dutchPay.getActiveParticipants());
        Map<Long, DutchPayParticipant> byId = existing.stream()
            .filter(pt -> pt.getRowId() != null)
            .collect(Collectors.toMap(DutchPayParticipant::getRowId, pt -> pt, (a, b) -> a));

        // 쓰기 전에 요청 전체를 훑어 둔다 — 아래에서 이름을 임시값으로 비켜 두므로,
        // 검증이 중간에 터지면 되돌릴 것만 늘어난다(롤백은 되지만 읽기가 어려워진다).
        List<DutchPayParticipant> matched = new ArrayList<>(participants.size());
        List<User> users = new ArrayList<>(participants.size());
        for (DutchPayServiceDto.ParticipantCommand pc : participants) {
            if (pc.amount() == null || pc.amount() <= 0) {
                throw new InvalidValueException(DeskErrorCode.DUTCH_PAY_INVALID_PARTICIPANT_AMOUNT);
            }
            User participantUser = null;
            if (pc.userRowId() != null) {
                participantUser = userRepository.findById(pc.userRowId())
                    .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
            }
            users.add(participantUser);
            matched.add(pc.rowId() != null ? byId.get(pc.rowId()) : null);
        }

        Set<DutchPayParticipant> kept =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        matched.stream().filter(java.util.Objects::nonNull).forEach(kept::add);

        // ── ① 목록에서 빠진 참가자를 <b>먼저</b> 지운다. id 로 매칭되지 않은 것도 여기 걸린다.
        for (DutchPayParticipant pt : existing) {
            if (!kept.contains(pt)) {
                pt.deleteParticipant();
            }
        }
        // ── ② 이름이 바뀌는 기존 행을 임시값으로 비켜 둔다.
        for (int i = 0; i < participants.size(); i++) {
            DutchPayParticipant found = matched.get(i);
            if (found != null && !found.getParticipantName().equalsIgnoreCase(names.get(i))) {
                found.parkNameForRename();
            }
        }
        // ── ③ 여기서 한 번 내보낸다. 이 flush 가 없으면 아래 신규 INSERT 가 위 UPDATE 보다
        //     먼저 나가(하이버네이트의 플러시 순서) "빠진 사람 자리에 같은 이름을 새로 넣는"
        //     저장과 "두 사람 이름 맞바꾸기" 가 활성 이름 UNIQUE 에 걸린다.
        dutchPayRepository.flush();

        // ── ④ 최종 이름·금액·결제자 적용 + 신규 추가.
        for (int i = 0; i < participants.size(); i++) {
            DutchPayServiceDto.ParticipantCommand pc = participants.get(i);
            DutchPayParticipant found = matched.get(i);
            if (found != null) {
                found.updateParticipant(users.get(i), names.get(i), pc.amount(), i == payerIndex);
            } else {
                dutchPay.addParticipant(DutchPayParticipant.create(
                    dutchPay, users.get(i), names.get(i), pc.amount(), i == payerIndex));
            }
        }
        flushOrRejectDuplicate();
    }

    private void addParticipants(DutchPay dutchPay, List<DutchPayServiceDto.ParticipantCommand> participants) {
        if (participants == null) return;
        List<String> names = validateNoDuplicateParticipants(participants);
        int payerIndex = resolvePayerIndex(participants);
        for (int i = 0; i < participants.size(); i++) {
            DutchPayServiceDto.ParticipantCommand pc = participants.get(i);
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
                dutchPay, participantUser, names.get(i), pc.amount(), i == payerIndex
            );
            dutchPay.addParticipant(participant);
        }
    }

    /**
     * 결제자가 목록의 몇 번째인지 정한다. 한 정산에 결제자는 한 명이다.
     *
     * <p>아무도 표시돼 있지 않으면 <b>첫 사람</b>을 결제자로 본다. 이 필드를 모르는 구버전
     * 앱이 여전히 정산을 만들 수 있어야 해서다 — 앱은 사용자가 원할 때 올리는 거라 백엔드보다
     * 늦게 갱신되는 기간이 반드시 생긴다. 기존 데이터를 마이그레이션이 채운 규칙과 같다.
     *
     * <p>둘 이상이면 거부한다. 그건 클라이언트 버그이고, 넘어가면 화면마다 다른 사람을
     * 결제자로 그리던 예전 증상으로 되돌아간다. MariaDB 에 조건부 UNIQUE 인덱스가 없어
     * DB 가 못 막으므로 여기가 유일한 방어선이다.
     */
    private int resolvePayerIndex(List<DutchPayServiceDto.ParticipantCommand> participants) {
        List<Integer> marked = java.util.stream.IntStream.range(0, participants.size())
            .filter(i -> Boolean.TRUE.equals(participants.get(i).isPayer()))
            .boxed()
            .toList();
        if (marked.size() > 1) {
            log.warn("더치페이 결제자 중복 - payerCount={}", marked.size());
            throw new InvalidValueException(DeskErrorCode.DUTCH_PAY_INVALID_PAYER);
        }
        return marked.isEmpty() ? 0 : marked.get(0);
    }

    /**
     * 한 정산 안에서 같은 사람을 두 번 넣지 못하게 한다 — 정규화된 이름을 요청 순서대로 돌려준다.
     *
     * <p>수정은 요청에 없는 행을 지우는 구조라 커밋 후 활성 집합 == 요청 목록이 된다.
     * 그래서 요청 안의 중복만 막으면 결과가 맞는다. 스코프는 사용자가 아니라 <b>정산 건</b>이다.
     *
     * <h4>고친 구멍 둘</h4>
     * ① <b>검사 갈래가 갈려 있었다.</b> 등록 사용자는 userRowId 집합에, 손으로 친 이름만 있는
     * 참가자는 이름 집합에 넣었다 — {@code {userRowId:5,"철수"}} 와 {@code {userRowId:null,"철수"}}
     * 가 둘 다 통과해 한 정산에 같은 이름 활성 행이 둘 생겼다. 이제 <b>이름은 userRowId 유무와
     * 무관하게 전원</b>을 본다(등록 사용자 중복은 그대로 따로 막는다).
     *
     * <p>② <b>이름 비교가 자바 문자열이었다.</b> {@code HashSet<String>.add} 라 대소문자 구분·
     * trim 없음 — '철수' 와 '철수 '(끝공백)가 통과했는데 DB 는 콜레이션({@code utf8mb4_unicode_ci},
     * PAD SPACE)상 같은 값으로 본다. 정규화한 뒤 {@code toLowerCase(Locale.ROOT)} 로 비교해
     * 판정을 DB 와 같게 맞춘다. <b>저장 값은 원문 대소문자 그대로</b>다.
     */
    private List<String> validateNoDuplicateParticipants(List<DutchPayServiceDto.ParticipantCommand> participants) {
        java.util.Set<Long> seenUserIds = new java.util.HashSet<>();
        java.util.Set<String> seenNames = new java.util.HashSet<>();
        List<String> names = new ArrayList<>(participants.size());
        for (DutchPayServiceDto.ParticipantCommand pc : participants) {
            if (pc.userRowId() != null && !seenUserIds.add(pc.userRowId())) {
                throw new InvalidValueException(DeskErrorCode.DUTCH_PAY_DUPLICATE_PARTICIPANT);
            }
            String name = NameNormalizer.require(pc.participantName(), FieldLimits.WIDE_NAME_MAX);
            if (!seenNames.add(name.toLowerCase(Locale.ROOT))) {
                throw new InvalidValueException(DeskErrorCode.DUTCH_PAY_DUPLICATE_PARTICIPANT);
            }
            names.add(name);
        }
        return names;
    }

    /** 위 검사를 빠져나간 경쟁·중복을 409 로 받는다 — 정산 건 안의 활성 이름 UNIQUE 가 마지막 판정자다. */
    private void flushOrRejectDuplicate() {
        try {
            dutchPayRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new InvalidValueException(DeskErrorCode.DUTCH_PAY_DUPLICATE_PARTICIPANT, e);
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
