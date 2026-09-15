package com.porest.desk.card.service;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.repository.CardBillingRepository;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 자정 카드 결제 배치는 <b>카드 한 장씩 따로</b> 커밋한다.
 *
 * <p>배치 전체가 트랜잭션 하나면 카드별 {@code try/catch} 가 예외는 삼켜도 트랜잭션에는
 * 이미 <b>rollback-only</b> 가 찍혀 있다. 마지막 커밋이 {@code UnexpectedRollbackException}
 * 으로 끝나면서 <b>그날 성공한 다른 카드의 청구 회차·이체까지 되돌아간다.</b> 로그에는
 * "성공 N, 실패 1" 로 남아 아무도 모른다 — 돈과 기록이 조용히 어긋나는 자리다.
 *
 * <p>단위 테스트로는 못 잡는다. mock 리포지토리는 rollback-only 표식을 만들지 않으므로
 * 깨진 코드도 통과한다. 그래서 진짜 트랜잭션에 붙인다.
 *
 * <p>트랜잭션을 걸지 않는다 — 배치가 자기 트랜잭션 경계를 갖는 것이 이 테스트의 대상이라,
 * 테스트가 트랜잭션을 들고 있으면 그 경계가 사라져 아무것도 재현되지 않는다. 대신 케이스마다
 * 사용자를 새로 만들어 서로 안 밟게 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CardPaymentBatchIsolationTest {

    @Autowired private CardPaymentService cardPaymentService;
    @Autowired private AssetRepository assetRepository;
    @Autowired private CardBillingRepository cardBillingRepository;
    @Autowired private AssetBalanceHistoryService balanceHistoryService;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private User newUser() {
        String id = "c" + UUID.randomUUID().toString().substring(0, 8);
        return transactionTemplate.execute(s ->
            userRepository.save(User.createUser(null, id, "테스터", id + "@porest.com")));
    }

    private Asset newAsset(User owner, String name, AssetType type,
                           Integer paymentDay, Asset paymentAsset) {
        return transactionTemplate.execute(s -> assetRepository.save(Asset.createAsset(
            owner, name, type, 0L, "KRW", null, null, null, null, 0, YNType.Y,
            null, type == AssetType.CREDIT_CARD ? 1_000_000L : null, paymentDay, paymentAsset)));
    }

    @Test
    @DisplayName("한 카드가 실패해도 다른 카드의 청구는 남는다 — 건마다 커밋한다")
    void oneCardFailureDoesNotRollBackTheOthers() {
        User u = newUser();
        LocalDate today = LocalDate.now();

        // 카드 A — 결제계좌를 지운 채로 남겨 두고 과납 잔액을 만든다.
        // 환급 스윕이 카드 → (지워진) 결제계좌로 이체를 시도하다 ASSET_NOT_FOUND 로 터진다.
        // 이 호출은 프록시를 지나는 assetService.createTransfer 라 rollback-only 가 찍힌다.
        Asset deadBank = newAsset(u, "지운통장", AssetType.BANK_ACCOUNT, null, null);
        Asset cardA = newAsset(u, "카드A", AssetType.CREDIT_CARD, today.getDayOfMonth(), deadBank);
        transactionTemplate.executeWithoutResult(s -> {
            Asset managed = assetRepository.findById(cardA.getRowId()).orElseThrow();
            balanceHistoryService.recordExpense(
                managed, null, ExpenseType.INCOME, 10_000L, today.atStartOfDay());
        });
        transactionTemplate.executeWithoutResult(s ->
            assetRepository.findById(deadBank.getRowId()).orElseThrow().deleteAsset());

        // 카드 B — 결제일이 오늘이고 쓴 게 없다. 회차 금액 0 이라 "건너뜀" 청구가 저장된다.
        // 이 행이 커밋돼 남아야 한다.
        Asset liveBank = newAsset(u, "멀쩡통장", AssetType.BANK_ACCOUNT, null, null);
        Asset cardB = newAsset(u, "카드B", AssetType.CREDIT_CARD, today.getDayOfMonth(), liveBank);

        assertThatCode(() -> cardPaymentService.processDueCardPayments(today))
            .as("배치가 통째로 롤백되면 여기서 UnexpectedRollbackException 이 터진다")
            .doesNotThrowAnyException();

        assertThat(cardBillingRepository.findByCardAssetRowId(cardB.getRowId()))
            .as("카드 B 의 청구가 카드 A 의 실패에 딸려 사라지면 안 된다")
            .isNotEmpty();
    }
}
