package com.porest.desk.dataimport.sms.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.InvalidValueException;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.dataimport.sms.domain.SmsCardMapping;
import com.porest.desk.dataimport.sms.repository.SmsCardMappingRepository;
import com.porest.desk.dataimport.sms.service.dto.SmsImportServiceDto;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.ExpenseService;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SmsImportServiceImpl — 결제 문자 해석·저장")
class SmsImportServiceImplTest {

    private static final Long USER = 1L;

    private static final String KB_SMS = """
        [Web발신]
        KB국민카드1234승인
        홍*동
        5,500원 일시불
        08/13 13:22
        스타벅스강남""";

    @Mock private ExpenseService expenseService;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private SmsCardMappingRepository cardMappingRepository;
    @InjectMocks private SmsImportServiceImpl sut;

    // ── 픽스처 헬퍼 ────────────────────────────────────────────

    private static Asset card(Long rowId, String name, String institution, AssetType type) {
        Asset asset = mock(Asset.class);
        given(asset.getRowId()).willReturn(rowId);
        given(asset.getAssetName()).willReturn(name);
        given(asset.getInstitution()).willReturn(institution);
        given(asset.getAssetType()).willReturn(type);
        return asset;
    }

    private static ExpenseCategory category(Long rowId, String name, ExpenseCategory parent) {
        ExpenseCategory c = mock(ExpenseCategory.class);
        given(c.getRowId()).willReturn(rowId);
        given(c.getCategoryName()).willReturn(name);
        given(c.getExpenseType()).willReturn(ExpenseType.EXPENSE);
        given(c.getParent()).willReturn(parent);
        return c;
    }

    private static SmsCardMapping mapping(Long rowId, String cardHint, Long assetRowId, YNType deleted) {
        SmsCardMapping m = SmsCardMapping.create(USER, cardHint, assetRowId);
        ReflectionTestUtils.setField(m, "rowId", rowId);
        ReflectionTestUtils.setField(m, "isDeleted", deleted);
        return m;
    }

    private SmsImportServiceDto.CommitCommand commitCommand(Long assetRowId, boolean remember) {
        return new SmsImportServiceDto.CommitCommand(
            USER, KB_SMS, assetRowId, 10L, 5_500L, "스타벅스강남", null,
            LocalDateTime.of(2026, 8, 13, 13, 22), "CARD", null, null, null, null, remember);
    }

    private void givenExpenseCreated(Long expenseRowId) {
        ExpenseServiceDto.ExpenseInfo info = mock(ExpenseServiceDto.ExpenseInfo.class);
        given(info.rowId()).willReturn(expenseRowId);
        given(expenseService.createExpense(any(ExpenseServiceDto.CreateCommand.class)))
            .willReturn(info);
    }

    @Nested
    @DisplayName("parse — 자산 매칭")
    class AssetMatching {

        @Test
        @DisplayName("기억해 둔 매핑이 있으면 그 자산으로 확정한다")
        void remembered() {
            Asset kb = card(100L, "KB 국민 체크", "KB국민카드", AssetType.CHECK_CARD);
            SmsCardMapping saved = mapping(1L, "KB국민카드|1234", 100L, YNType.N);
            given(assetRepository.findByUser(USER)).willReturn(List.of(kb));
            given(cardMappingRepository.findByCardHintIncludingDeleted(USER, "KB국민카드|1234"))
                .willReturn(Optional.of(saved));

            var result = sut.parse(KB_SMS, USER);

            assertThat(result.assetRowId()).isEqualTo(100L);
            assertThat(result.assetRemembered()).isTrue();
            assertThat(result.assetCandidates()).isEmpty();
        }

        @Test
        @DisplayName("매핑된 자산이 그 사이 삭제됐으면 무시하고 후보를 다시 고른다")
        void rememberedButAssetGone() {
            Asset other = card(200L, "신한 카드", "신한카드", AssetType.CREDIT_CARD);
            given(assetRepository.findByUser(USER)).willReturn(List.of(other));
            given(cardMappingRepository.findByCardHintIncludingDeleted(USER, "KB국민카드|1234"))
                .willReturn(Optional.of(mapping(1L, "KB국민카드|1234", 999L, YNType.N)));

            var result = sut.parse(KB_SMS, USER);

            assertThat(result.assetRemembered()).isFalse();
            assertThat(result.assetRowId()).isEqualTo(200L); // 카드가 하나뿐이라 그걸 제안
        }

        @Test
        @DisplayName("해제된 매핑은 살아 있는 것으로 치지 않는다")
        void deletedMapping() {
            Asset kb = card(100L, "KB 국민 체크", "KB국민카드", AssetType.CHECK_CARD);
            given(assetRepository.findByUser(USER)).willReturn(List.of(kb));
            given(cardMappingRepository.findByCardHintIncludingDeleted(USER, "KB국민카드|1234"))
                .willReturn(Optional.of(mapping(1L, "KB국민카드|1234", 100L, YNType.Y)));

            var result = sut.parse(KB_SMS, USER);

            assertThat(result.assetRemembered()).isFalse();
        }

        @Test
        @DisplayName("카드 끝자리가 자산명에 있으면 그 카드만 후보로 좁힌다")
        void byLast4() {
            Asset a = card(100L, "국민 1234", "KB국민카드", AssetType.CREDIT_CARD);
            Asset b = card(200L, "국민 9999", "KB국민카드", AssetType.CREDIT_CARD);
            given(assetRepository.findByUser(USER)).willReturn(List.of(a, b));
            given(cardMappingRepository.findByCardHintIncludingDeleted(anyLong(), anyString()))
                .willReturn(Optional.empty());

            var result = sut.parse(KB_SMS, USER);

            assertThat(result.assetCandidates()).extracting("rowId").containsExactly(100L);
            assertThat(result.assetRowId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("카드사만 맞는 카드가 여럿이면 확정하지 않고 후보만 준다")
        void multipleCandidates() {
            Asset a = card(100L, "국민 생활", "KB국민카드", AssetType.CREDIT_CARD);
            Asset b = card(200L, "국민 여행", "KB국민카드", AssetType.CREDIT_CARD);
            given(assetRepository.findByUser(USER)).willReturn(List.of(a, b));
            given(cardMappingRepository.findByCardHintIncludingDeleted(anyLong(), anyString()))
                .willReturn(Optional.empty());

            var result = sut.parse(KB_SMS, USER);

            assertThat(result.assetRowId()).isNull();
            assertThat(result.assetCandidates()).extracting("rowId").containsExactly(100L, 200L);
        }

        @Test
        @DisplayName("카드 자산이 하나도 없으면 후보도 비운다")
        void noCardAssets() {
            Asset bank = card(300L, "월급통장", "국민은행", AssetType.BANK_ACCOUNT);
            given(assetRepository.findByUser(USER)).willReturn(List.of(bank));
            given(cardMappingRepository.findByCardHintIncludingDeleted(anyLong(), anyString()))
                .willReturn(Optional.empty());

            var result = sut.parse(KB_SMS, USER);

            assertThat(result.assetRowId()).isNull();
            assertThat(result.assetCandidates()).isEmpty();
        }
    }

    @Nested
    @DisplayName("parse — 카테고리 추론")
    class CategoryInference {

        @Test
        @DisplayName("같은 가맹점의 지난 거래 카테고리가 1순위")
        void byRecentMerchant() {
            given(assetRepository.findByUser(USER)).willReturn(List.of());
            ExpenseCategory work = category(77L, "업무경비", null);
            given(expenseRepository.findRecentCategoryRowIdByMerchant(USER, "스타벅스강남", ExpenseType.EXPENSE))
                .willReturn(Optional.of(77L));
            given(expenseCategoryRepository.findAllByUser(USER)).willReturn(List.of(work));

            var result = sut.parse(KB_SMS, USER);

            assertThat(result.categoryRowId()).isEqualTo(77L);
            assertThat(result.categoryName()).isEqualTo("업무경비");
        }

        @Test
        @DisplayName("지난 거래의 카테고리가 삭제됐으면 쓰지 않는다")
        void recentCategoryDeleted() {
            given(assetRepository.findByUser(USER)).willReturn(List.of());
            given(expenseRepository.findRecentCategoryRowIdByMerchant(USER, "스타벅스강남", ExpenseType.EXPENSE))
                .willReturn(Optional.of(77L));
            given(expenseCategoryRepository.findAllByUser(USER)).willReturn(List.of());

            var result = sut.parse(KB_SMS, USER);

            assertThat(result.categoryRowId()).isNull();
        }

        @Test
        @DisplayName("이력이 없으면 키워드 사전으로 기존 카테고리를 찾는다")
        void byKeywordHint() {
            given(assetRepository.findByUser(USER)).willReturn(List.of());
            ExpenseCategory cafe = category(50L, "카페", null);
            given(expenseRepository.findRecentCategoryRowIdByMerchant(anyLong(), anyString(), any()))
                .willReturn(Optional.empty());
            given(expenseCategoryRepository.findAllByUser(USER)).willReturn(List.of(cafe));

            var result = sut.parse(KB_SMS, USER);

            assertThat(result.categoryRowId()).isEqualTo(50L);
            assertThat(result.categoryName()).isEqualTo("카페");
        }

        @Test
        @DisplayName("자식이 달린 부모 카테고리는 제안하지 않는다 — 거래는 leaf 에만 달린다")
        void skipParentCategory() {
            ExpenseCategory parent = category(50L, "카페", null);
            ExpenseCategory child = category(51L, "커피", parent);
            given(assetRepository.findByUser(USER)).willReturn(List.of());
            given(expenseRepository.findRecentCategoryRowIdByMerchant(anyLong(), anyString(), any()))
                .willReturn(Optional.empty());
            given(expenseCategoryRepository.findAllByUser(USER)).willReturn(List.of(parent, child));

            var result = sut.parse(KB_SMS, USER);

            assertThat(result.categoryRowId()).isEqualTo(51L); // 부모(50) 가 아니라 자식(51)
        }

        @Test
        @DisplayName("맞는 카테고리가 없으면 비워 사용자가 고르게 한다 — 새로 만들지 않는다")
        void noCategoryCreated() {
            given(assetRepository.findByUser(USER)).willReturn(List.of());
            given(expenseRepository.findRecentCategoryRowIdByMerchant(anyLong(), anyString(), any()))
                .willReturn(Optional.empty());
            given(expenseCategoryRepository.findAllByUser(USER)).willReturn(List.of());

            var result = sut.parse(KB_SMS, USER);

            assertThat(result.categoryRowId()).isNull();
            verify(expenseCategoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("parse — 인식 실패")
    class NotRecognized {

        @Test
        @DisplayName("결제 문자가 아니면 빈 결과 — 자산·카테고리 조회조차 하지 않는다")
        void notPayment() {
            var result = sut.parse("오늘 저녁에 만나자", USER);

            assertThat(result.matched()).isFalse();
            verify(assetRepository, never()).findByUser(anyLong());
            verify(expenseCategoryRepository, never()).findAllByUser(anyLong());
        }
    }

    @Nested
    @DisplayName("commit — 저장")
    class Commit {

        @Test
        @DisplayName("ExpenseService 를 거쳐 지출을 만든다 — 확정 값 그대로")
        void createsExpense() {
            givenExpenseCreated(500L);

            var result = sut.commit(commitCommand(100L, false));

            ArgumentCaptor<ExpenseServiceDto.CreateCommand> captor =
                ArgumentCaptor.forClass(ExpenseServiceDto.CreateCommand.class);
            verify(expenseService).createExpense(captor.capture());
            ExpenseServiceDto.CreateCommand cmd = captor.getValue();

            assertThat(cmd.userRowId()).isEqualTo(USER);
            assertThat(cmd.expenseType()).isEqualTo(ExpenseType.EXPENSE);
            assertThat(cmd.amount()).isEqualTo(5_500L);
            assertThat(cmd.merchant()).isEqualTo("스타벅스강남");
            assertThat(cmd.categoryRowId()).isEqualTo(10L);
            assertThat(cmd.assetRowId()).isEqualTo(100L);
            assertThat(cmd.paymentMethod()).isEqualTo("CARD");
            assertThat(cmd.expenseDate()).isEqualTo(LocalDateTime.of(2026, 8, 13, 13, 22));
            assertThat(result.expenseRowId()).isEqualTo(500L);
        }

        @Test
        @DisplayName("결제수단을 안 보내면 카드로 채운다 — 폼 select 가 알아보는 코드여야 한다")
        void paymentMethodDefaultsToCard() {
            givenExpenseCreated(500L);
            var cmd = new SmsImportServiceDto.CommitCommand(
                USER, KB_SMS, null, 10L, 5_500L, "스타벅스강남", null,
                LocalDateTime.of(2026, 8, 13, 13, 22), null, null, null, null, null, false);

            sut.commit(cmd);

            ArgumentCaptor<ExpenseServiceDto.CreateCommand> captor =
                ArgumentCaptor.forClass(ExpenseServiceDto.CreateCommand.class);
            verify(expenseService).createExpense(captor.capture());
            assertThat(captor.getValue().paymentMethod()).isEqualTo("CARD");
        }

        @Test
        @DisplayName("취소 문자는 저장하지 않는다 — 결제와 취소가 둘 다 지출로 쌓인다")
        void rejectsCancel() {
            String cancelSms = """
                [Web발신]
                KB국민카드1234승인취소
                5,500원
                08/13 14:00
                스타벅스강남""";
            var cmd = new SmsImportServiceDto.CommitCommand(
                USER, cancelSms, 100L, 10L, 5_500L, "스타벅스강남", null,
                LocalDateTime.of(2026, 8, 13, 14, 0), "CARD", null, null, null, null, false);

            assertThatThrownBy(() -> sut.commit(cmd))
                .isInstanceOf(InvalidValueException.class);
            verify(expenseService, never()).createExpense(any(ExpenseServiceDto.CreateCommand.class));
        }

        @Test
        @DisplayName("결제 문자가 아닌 원문이면 거부한다")
        void rejectsNonPayment() {
            var cmd = new SmsImportServiceDto.CommitCommand(
                USER, "오늘 저녁에 만나자", 100L, 10L, 5_500L, "x", null,
                LocalDateTime.of(2026, 8, 13, 14, 0), "CARD", null, null, null, null, false);

            assertThatThrownBy(() -> sut.commit(cmd))
                .isInstanceOf(InvalidValueException.class);
            verify(expenseService, never()).createExpense(any(ExpenseServiceDto.CreateCommand.class));
        }
    }

    @Nested
    @DisplayName("commit — 카드 기억")
    class RememberCard {

        @Test
        @DisplayName("체크하면 (카드힌트 → 자산) 을 새로 적어 둔다")
        void savesNewMapping() {
            givenExpenseCreated(500L);
            given(cardMappingRepository.findByCardHintIncludingDeleted(USER, "KB국민카드|1234"))
                .willReturn(Optional.empty());

            var result = sut.commit(commitCommand(100L, true));

            ArgumentCaptor<SmsCardMapping> captor = ArgumentCaptor.forClass(SmsCardMapping.class);
            verify(cardMappingRepository).save(captor.capture());
            assertThat(captor.getValue().getCardHint()).isEqualTo("KB국민카드|1234");
            assertThat(captor.getValue().getAssetRowId()).isEqualTo(100L);
            assertThat(result.cardRemembered()).isTrue();
        }

        @Test
        @DisplayName("이미 있으면 새 행 대신 자산만 갈아끼운다 — 유니크 제약 때문")
        void relinksExisting() {
            givenExpenseCreated(500L);
            SmsCardMapping existing = mapping(1L, "KB국민카드|1234", 999L, YNType.Y);
            given(cardMappingRepository.findByCardHintIncludingDeleted(USER, "KB국민카드|1234"))
                .willReturn(Optional.of(existing));

            sut.commit(commitCommand(100L, true));

            verify(cardMappingRepository, never()).save(any());
            assertThat(existing.getAssetRowId()).isEqualTo(100L);
            assertThat(existing.getIsDeleted()).isEqualTo(YNType.N);
        }

        @Test
        @DisplayName("자산을 안 골랐으면 기억할 것도 없다")
        void skipsWhenNoAsset() {
            givenExpenseCreated(500L);

            var result = sut.commit(commitCommand(null, true));

            verify(cardMappingRepository, never()).save(any());
            assertThat(result.cardRemembered()).isFalse();
        }
    }

    @Nested
    @DisplayName("카드 매핑 관리")
    class Mappings {

        @Test
        @DisplayName("목록 — 자산명을 한 번의 조회로 붙인다")
        void list() {
            SmsCardMapping saved = mapping(1L, "KB국민카드|1234", 100L, YNType.N);
            Asset kb = card(100L, "KB 국민 체크", "KB국민카드", AssetType.CHECK_CARD);
            given(cardMappingRepository.findAllActiveByUser(USER)).willReturn(List.of(saved));
            given(assetRepository.findByUser(USER)).willReturn(List.of(kb));

            var list = sut.getCardMappings(USER);

            assertThat(list).hasSize(1);
            assertThat(list.get(0).assetName()).isEqualTo("KB 국민 체크");
            verify(assetRepository).findByUser(USER);
        }

        @Test
        @DisplayName("해제 — 없는 매핑이면 404")
        void deleteMissing() {
            given(cardMappingRepository.findActiveById(9L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sut.deleteCardMapping(9L, USER))
                .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("해제 — 남의 매핑은 건드릴 수 없다")
        void deleteOthers() {
            given(cardMappingRepository.findActiveById(9L))
                .willReturn(Optional.of(mapping(9L, "KB국민카드|1234", 100L, YNType.N)));

            assertThatThrownBy(() -> sut.deleteCardMapping(9L, 999L))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("해제 — 내 매핑이면 삭제 표시한다")
        void deleteOwn() {
            SmsCardMapping m = mapping(9L, "KB국민카드|1234", 100L, YNType.N);
            given(cardMappingRepository.findActiveById(9L)).willReturn(Optional.of(m));

            sut.deleteCardMapping(9L, USER);

            assertThat(m.getIsDeleted()).isEqualTo(YNType.Y);
        }
    }

    private static User user(Long rowId) {
        User u = mock(User.class);
        given(u.getRowId()).willReturn(rowId);
        return u;
    }
}
