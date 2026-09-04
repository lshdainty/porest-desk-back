package com.porest.desk.stock.service;

import com.porest.core.exception.DuplicateException;
import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.type.MasterSource;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.domain.StockWatchGroup;
import com.porest.desk.stock.domain.StockWatchItem;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.repository.StockWatchGroupRepository;
import com.porest.desk.stock.repository.StockWatchItemRepository;
import com.porest.desk.stock.service.dto.StockWatchServiceDto;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 관심목록 서비스 테스트 — 소유권·상한·중복·심볼 해석(KR/US 우선)·재추가 undelete 를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class StockWatchServiceImplTest {

    @Mock private StockWatchGroupRepository groupRepository;
    @Mock private StockWatchItemRepository itemRepository;
    @Mock private StockMasterRepository stockMasterRepository;
    private StockWatchServiceImpl service;

    // 해석기는 목이 아니라 진짜를 물린다 — 심볼 해석 규칙까지 여기서 함께 지켜진다.
    @BeforeEach
    void setUpService() {
        service = new StockWatchServiceImpl(groupRepository, itemRepository,
            new StockMasterResolver(stockMasterRepository));
    }

    private static final long USER = 1L;

    private StockMaster master(StockMarket market, String symbol, String nameKr) {
        return StockMaster.create(MasterSource.KIS,
            InstrumentRecord.kis(market, symbol, null, null, nameKr, null, StockSecurityType.STOCK,
                market.getCountryCode().equals("KR") ? "KRW" : "USD"));
    }

    private StockWatchGroup ownedGroup() {
        return StockWatchGroup.create(USER, "관심", 0);
    }

    @Test
    @DisplayName("그룹 생성 — 사용자별 상한(20개)을 넘으면 거부한다")
    void createGroup_rejectsOverLimit() {
        given(groupRepository.countActiveByUser(USER)).willReturn(20L);

        assertThatThrownBy(() -> service.createGroup(USER, "새 그룹"))
            .isInstanceOf(InvalidValueException.class);
        verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("그룹 생성 — 같은 이름의 활성 그룹이 있으면 거부한다")
    void createGroup_rejectsDuplicateName() {
        given(groupRepository.countActiveByUser(USER)).willReturn(1L);
        given(groupRepository.existsActiveByUserAndName(USER, "관심", null)).willReturn(true);

        assertThatThrownBy(() -> service.createGroup(USER, " 관심 "))
            .isInstanceOf(DuplicateException.class);
    }

    @Test
    @DisplayName("그룹 생성 — 이름을 trim 하고 기존 최대 sortOrder 다음 순서로 저장한다")
    void createGroup_trimsNameAndAppendsOrder() {
        given(groupRepository.countActiveByUser(USER)).willReturn(1L);
        given(groupRepository.existsActiveByUserAndName(USER, "미국 기술주", null)).willReturn(false);
        given(groupRepository.findAllActiveByUser(USER)).willReturn(List.of(ownedGroup()));
        given(groupRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        StockWatchServiceDto.GroupInfo info = service.createGroup(USER, " 미국 기술주 ");

        assertThat(info.groupName()).isEqualTo("미국 기술주");
        assertThat(info.sortOrder()).isEqualTo(1);
        assertThat(info.items()).isEmpty();
    }

    @Test
    @DisplayName("남의 그룹에는 종목을 추가할 수 없다 — NOT_FOUND 로 응답해 존재 여부도 숨긴다")
    void addItem_rejectsUnownedGroup() {
        given(groupRepository.findActiveByIdAndUser(10L, USER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(USER, 10L, "AAPL", null))
            .isInstanceOf(EntityNotFoundException.class);
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("종목 추가 — 시장 미지정 심볼은 KR/US 시장을 우선 해석한다 (6자리 코드 충돌 대비)")
    void addItem_prefersKrUsWhenMarketUnspecified() {
        given(groupRepository.findActiveByIdAndUser(10L, USER)).willReturn(Optional.of(ownedGroup()));
        StockMaster shanghai = master(StockMarket.SHS, "600519", "귀주모태주");
        StockMaster korea = master(StockMarket.KOSPI, "600519", "가상의국내종목");
        given(stockMasterRepository.findAllActiveBySymbol("600519")).willReturn(List.of(shanghai, korea));
        given(itemRepository.findByGroupAndStockIncludingDeleted(any(), any())).willReturn(Optional.empty());
        given(itemRepository.countActiveByGroup(10L)).willReturn(0L);
        given(itemRepository.findAllActiveByGroup(10L)).willReturn(List.of());
        given(itemRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        StockWatchServiceDto.ItemInfo info = service.addItem(USER, 10L, "600519", null);

        assertThat(info.marketCode()).isEqualTo(StockMarket.KOSPI);
        assertThat(info.countryCode()).isEqualTo("KR");
    }

    @Test
    @DisplayName("종목 추가 — 마스터에 없는 심볼은 STOCK_NOT_FOUND")
    void addItem_rejectsUnknownSymbol() {
        given(groupRepository.findActiveByIdAndUser(10L, USER)).willReturn(Optional.of(ownedGroup()));
        given(stockMasterRepository.findAllActiveBySymbol("NOPE")).willReturn(List.of());

        assertThatThrownBy(() -> service.addItem(USER, 10L, "NOPE", null))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("종목 추가 — 이미 담긴 종목은 저장 없이 그대로 돌려준다 (멱등)")
    void addItem_isIdempotentForActiveItem() {
        given(groupRepository.findActiveByIdAndUser(10L, USER)).willReturn(Optional.of(ownedGroup()));
        StockMaster apple = master(StockMarket.NAS, "AAPL", "애플");
        given(stockMasterRepository.findActiveByMarketAndSymbol(StockMarket.NAS, "AAPL"))
            .willReturn(Optional.of(apple));
        StockWatchItem active = StockWatchItem.create(10L, null, 0);
        given(itemRepository.findByGroupAndStockIncludingDeleted(any(), any())).willReturn(Optional.of(active));

        StockWatchServiceDto.ItemInfo info = service.addItem(USER, 10L, "AAPL", StockMarket.NAS);

        assertThat(info.symbol()).isEqualTo("AAPL");
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("종목 추가 — 삭제했던 종목은 새 행 대신 되살린다 (유니크 제약 방어)")
    void addItem_restoresDeletedItem() {
        given(groupRepository.findActiveByIdAndUser(10L, USER)).willReturn(Optional.of(ownedGroup()));
        StockMaster apple = master(StockMarket.NAS, "AAPL", "애플");
        given(stockMasterRepository.findActiveByMarketAndSymbol(StockMarket.NAS, "AAPL"))
            .willReturn(Optional.of(apple));
        StockWatchItem deleted = StockWatchItem.create(10L, null, 0);
        deleted.delete();
        given(itemRepository.findByGroupAndStockIncludingDeleted(any(), any())).willReturn(Optional.of(deleted));
        given(itemRepository.countActiveByGroup(10L)).willReturn(3L);
        given(itemRepository.findAllActiveByGroup(10L)).willReturn(List.of());

        service.addItem(USER, 10L, "AAPL", StockMarket.NAS);

        assertThat(deleted.isDeleted()).isFalse();
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("종목 추가 — 그룹당 상한(100개)을 넘으면 거부한다")
    void addItem_rejectsOverItemLimit() {
        given(groupRepository.findActiveByIdAndUser(10L, USER)).willReturn(Optional.of(ownedGroup()));
        StockMaster apple = master(StockMarket.NAS, "AAPL", "애플");
        given(stockMasterRepository.findActiveByMarketAndSymbol(StockMarket.NAS, "AAPL"))
            .willReturn(Optional.of(apple));
        given(itemRepository.findByGroupAndStockIncludingDeleted(any(), any())).willReturn(Optional.empty());
        given(itemRepository.countActiveByGroup(10L)).willReturn(100L);

        assertThatThrownBy(() -> service.addItem(USER, 10L, "AAPL", StockMarket.NAS))
            .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("그룹 삭제 — 소속 활성 종목도 함께 soft delete 한다")
    void deleteGroup_cascadesItems() {
        StockWatchGroup group = ownedGroup();
        given(groupRepository.findActiveByIdAndUser(10L, USER)).willReturn(Optional.of(group));
        StockWatchItem item1 = StockWatchItem.create(10L, 100L, 0);
        StockWatchItem item2 = StockWatchItem.create(10L, 200L, 1);
        given(itemRepository.findAllActiveByGroup(10L)).willReturn(List.of(item1, item2));

        service.deleteGroup(USER, 10L);

        assertThat(group.isDeleted()).isTrue();
        assertThat(item1.isDeleted()).isTrue();
        assertThat(item2.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("종목 제거 — 남의 그룹 종목이면 NOT_FOUND")
    void removeItem_rejectsUnownedItem() {
        StockWatchItem item = StockWatchItem.create(99L, 100L, 0);
        given(itemRepository.findActiveById(5L)).willReturn(Optional.of(item));
        given(groupRepository.findActiveByIdAndUser(99L, USER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeItem(USER, 5L))
            .isInstanceOf(EntityNotFoundException.class);
        assertThat(item.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("그룹 이름 변경 — 다른 활성 그룹과 겹치면 거부, 자기 이름 그대로는 허용")
    void renameGroup_checksDuplicateExceptSelf() {
        StockWatchGroup group = ownedGroup();
        given(groupRepository.findActiveByIdAndUser(10L, USER)).willReturn(Optional.of(group));
        given(groupRepository.existsActiveByUserAndName(USER, "미국 기술주", 10L)).willReturn(true);

        assertThatThrownBy(() -> service.renameGroup(USER, 10L, "미국 기술주"))
            .isInstanceOf(DuplicateException.class);

        // 자기 이름 그대로 저장은 자기 자신을 뺀 검사를 통과한다.
        given(itemRepository.findAllActiveByUserWithStock(USER)).willReturn(List.of());
        StockWatchServiceDto.GroupInfo info = service.renameGroup(USER, 10L, "관심");
        assertThat(info.groupName()).isEqualTo("관심");
    }

    @Test
    @DisplayName("그룹 이름 변경 — 대소문자만 바꾸는 개명이 통과한다(자기 자신 제외를 DB 로 내린 결과)")
    void renameGroup_allowsCaseOnlyRename() {
        StockWatchGroup group = StockWatchGroup.create(USER, "tech", 0);
        given(groupRepository.findActiveByIdAndUser(10L, USER)).willReturn(Optional.of(group));
        // 자기 자신을 뺀 검사라 false — 종전엔 자바 equals(대소문자 구분)가 "이름이 바뀌었다" 로
        // 통과시킨 뒤 DB 검사(콜레이션 _ci)가 자기 자신을 찾아 409 를 던졌다.
        given(groupRepository.existsActiveByUserAndName(USER, "TECH", 10L)).willReturn(false);
        given(itemRepository.findAllActiveByUserWithStock(USER)).willReturn(List.of());

        StockWatchServiceDto.GroupInfo info = service.renameGroup(USER, 10L, "TECH");

        assertThat(info.groupName()).isEqualTo("TECH");
    }

    @Test
    @DisplayName("그룹 생성 — 유니크 제약 위반(동시 저장 경쟁)은 500 이 아니라 409 로 나간다")
    void createGroup_translatesConstraintViolation() {
        given(groupRepository.countActiveByUser(USER)).willReturn(1L);
        given(groupRepository.existsActiveByUserAndName(USER, "관심", null)).willReturn(false);
        given(groupRepository.findAllActiveByUser(USER)).willReturn(List.of());
        given(groupRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        willThrow(new DataIntegrityViolationException("UK_stock_watch_group_user_active_name"))
            .given(groupRepository).flush();

        assertThatThrownBy(() -> service.createGroup(USER, "관심"))
            .isInstanceOf(DuplicateException.class);
    }
}
