package com.porest.desk.memo.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.memo.domain.MemoTag;
import com.porest.desk.memo.repository.MemoRepository;
import com.porest.desk.memo.repository.MemoTagRepository;
import com.porest.desk.memo.service.dto.MemoTagServiceDto;
import com.porest.desk.support.exception.ConstraintViolations;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 메모 태그 서비스 — 소유권 가드 · 개명 동기화 · 삭제 시 되살아남 방지 · 확보 경쟁.
 *
 * <p>{@code TodoTagServiceImplTest} 의 미러다. 다른 점은 매핑 테이블이 없어 사용 수와 정리가
 * 전부 {@code memo} 한 테이블에서 끝난다는 것뿐이다.
 */
@ExtendWith(MockitoExtension.class)
class MemoTagServiceImplTest {

    @Mock private MemoTagRepository memoTagRepository;
    @Mock private MemoRepository memoRepository;
    @Mock private UserRepository userRepository;
    // 생성자 주입 — findOrCreateByName 이 새 트랜잭션 템플릿을 쓰므로 트랜잭션 매니저가 필요하다.
    // 목은 getTransaction/commit 을 무해하게 흘려보내므로 콜백은 그대로 돈다.
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private MemoTagServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private MemoTag ownedTag(long rowId, String name) {
        MemoTag t = MemoTag.createTag(user(USER_ID), name, "#ffffff");
        ReflectionTestUtils.setField(t, "rowId", rowId);
        return t;
    }

    private MemoTag othersTag() {
        MemoTag t = mock(MemoTag.class);
        given(t.getUser()).willReturn(user(999L));
        return t;
    }

    // ── 소유권 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateTag — 남의 태그는 수정 불가")
    void updateRejectsOthers() {
        // othersTag() 안에서 given(...) 이 돌므로 바깥 given(...) 인자로 바로 넣으면
        // Mockito 가 스터빙이 안 끝났다고 본다(UnfinishedStubbingException). 먼저 만든다.
        MemoTag others = othersTag();
        given(memoTagRepository.findById(5L)).willReturn(Optional.of(others));

        assertThatThrownBy(() -> sut.updateTag(5L, USER_ID, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteTag — 남의 태그는 삭제 불가(남의 메모를 건드리지도 않는다)")
    void deleteRejectsOthers() {
        MemoTag others = othersTag();
        given(memoTagRepository.findById(5L)).willReturn(Optional.of(others));

        assertThatThrownBy(() -> sut.deleteTag(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
        verify(memoRepository, never()).clearTag(anyLong(), anyLong(), anyString());
    }

    // ── 삭제: 되살아남 방지(QA #88) ─────────────────────────────────────────

    /**
     * QA #88 — 태그 행만 지우면 {@code memo.tag} 에 이름이 남아, 그 메모를 다음에 저장할 때
     * 서버가 같은 이름의 태그를 색 없이 새로 만든다(저장할 때마다 하나씩).
     *
     * <p>그래서 문자열과 FK 를 <b>함께</b> 끊는다 — 한쪽만 끊으면 나머지 한쪽이 되살린다.
     * WHERE 에 실리는 이름은 <b>soft-delete 전</b> 값이어야 한다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code deleteTag} 에서 {@code clearTag} 호출을
     * 지우면(= 태그 행만 지우던 종전 동작) 아래 단언이 깨진다.
     */
    @Test
    @DisplayName("deleteTag — 그 태그를 쓰던 메모의 tag 문자열과 FK 를 함께 비운다")
    void deleteClearsMemoTagStringAndFk() {
        MemoTag tag = ownedTag(5L, "업무");
        given(memoTagRepository.findById(5L)).willReturn(Optional.of(tag));

        sut.deleteTag(5L, USER_ID);

        verify(memoRepository).clearTag(USER_ID, 5L, "업무");
    }

    // ── 등록 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createTag — 활성 태그 중 같은 이름이 있으면 거부(중복 방지)")
    void createRejectsDuplicateActiveName() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(memoTagRepository.existsActiveByUserAndName(USER_ID, "일상", null)).willReturn(true);

        assertThatThrownBy(() -> sut.createTag(new MemoTagServiceDto.CreateCommand(USER_ID, "일상", "#ffffff")))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.MEMO_TAG_DUPLICATE_NAME);
    }

    @Test
    @DisplayName("createTag — 이름 앞뒤 공백은 저장 전에 잘린다(선행공백이 DB 판정과 어긋나던 자리)")
    void createTrimsName() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var info = sut.createTag(new MemoTagServiceDto.CreateCommand(USER_ID, "  일상 ", "#ffffff"));

        assertThat(info.tagName()).isEqualTo("일상");
        verify(memoTagRepository).existsActiveByUserAndName(USER_ID, "일상", null);
    }

    @Test
    @DisplayName("createTag — 빈 이름·공백뿐인 이름은 400 으로 거절한다")
    void createRejectsBlankName() {
        assertThatThrownBy(() -> sut.createTag(new MemoTagServiceDto.CreateCommand(USER_ID, " ", "#ffffff")))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("createTag — 유니크 위반(동시 저장 경쟁)은 500 이 아니라 409 로 나간다")
    void translatesConstraintViolation() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(memoTagRepository.existsActiveByUserAndName(USER_ID, "일상", null)).willReturn(false);
        willThrow(ConstraintViolations.unique("UK_memo_tag_user_active_name"))
                .given(memoTagRepository).flush();

        assertThatThrownBy(() -> sut.createTag(new MemoTagServiceDto.CreateCommand(USER_ID, "일상", "#ffffff")))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.MEMO_TAG_DUPLICATE_NAME);
    }

    /**
     * QA #81 — <b>UNIQUE 가 아닌 위반은 이 도메인이 손대지 않는다.</b> 값을 하나 빼먹고 보낸
     * 요청이 있지도 않은 중복을 이유로 거절당하면 안 된다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code flushOrRejectDuplicate} 의
     * {@code if (!IntegrityViolations.isUnique(e)) throw e;} 한 줄을 지우면 곧바로 깨진다.
     */
    @Test
    @DisplayName("createTag — NOT NULL 위반은 이름 중복으로 번역하지 않고 그대로 올린다")
    void doesNotTranslateNonUniqueViolation() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(memoTagRepository.existsActiveByUserAndName(USER_ID, "일상", null)).willReturn(false);
        willThrow(ConstraintViolations.notNull("COLOR")).given(memoTagRepository).flush();

        assertThatThrownBy(() -> sut.createTag(new MemoTagServiceDto.CreateCommand(USER_ID, "일상", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── 개명 동기화 ───────────────────────────────────────────────────────

    /**
     * 태그를 개명하면 {@code memo.tag} 에 남은 옛 이름도 따라 옮긴다. 안 옮기면 그 메모를
     * 다음에 저장할 때 서버가 옛 이름의 태그를 다시 만든다.
     *
     * <p><b>이 테스트의 핵심은 WHERE 에 실린 값이다.</b> 옛 이름을 {@code tag.updateTag(...)}
     * <b>뒤에서</b> 읽으면 이미 새 이름이라 {@code renameTag(1, 5, "회사", "회사")} 가 되어
     * 0 행을 고치고 조용히 통과한다 — 그래서 인자를 못 박아 검증한다(할 일이 밟은 함정).
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code MemoTagServiceImpl.updateTag} 의
     * {@code String previousName = tag.getTagName();} 를 {@code tag.updateTag(...)} 아래로
     * 옮기면 아래가 {@code renameTag(1, 5, "회사", "회사")} 를 보고 깨진다.
     */
    @Test
    @DisplayName("updateTag — 개명하면 옛 이름을 쓰던 메모의 tag 도 새 이름으로 옮긴다")
    void renameMovesMemoTagString() {
        MemoTag tag = ownedTag(5L, "업무");
        given(memoTagRepository.findById(5L)).willReturn(Optional.of(tag));
        given(memoTagRepository.existsActiveByUserAndName(USER_ID, "회사", 5L)).willReturn(false);

        sut.updateTag(5L, USER_ID, new MemoTagServiceDto.UpdateCommand("회사", "#ffffff"));

        verify(memoRepository).renameTag(USER_ID, 5L, "업무", "회사");
    }

    @Test
    @DisplayName("updateTag — 이름이 그대로면(색만 바꿔도) 메모는 건드리지 않는다")
    void colorOnlyUpdateLeavesMemosAlone() {
        MemoTag tag = ownedTag(5L, "업무");
        given(memoTagRepository.findById(5L)).willReturn(Optional.of(tag));
        given(memoTagRepository.existsActiveByUserAndName(USER_ID, "업무", 5L)).willReturn(false);

        sut.updateTag(5L, USER_ID, new MemoTagServiceDto.UpdateCommand("업무", "#000000"));

        verify(memoRepository, never()).renameTag(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("updateTag — 다른 활성 태그와 이름이 겹치면 409")
    void updateRejectsDuplicateActiveName() {
        MemoTag tag = ownedTag(5L, "업무");
        given(memoTagRepository.findById(5L)).willReturn(Optional.of(tag));
        given(memoTagRepository.existsActiveByUserAndName(USER_ID, "개인", 5L)).willReturn(true);

        assertThatThrownBy(() -> sut.updateTag(5L, USER_ID, new MemoTagServiceDto.UpdateCommand("개인", "#ffffff")))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.MEMO_TAG_DUPLICATE_NAME);
        verify(memoRepository, never()).renameTag(anyLong(), anyLong(), anyString(), anyString());
    }

    // ── 사용 수 ───────────────────────────────────────────────────────────

    /**
     * 사용 수는 <b>이름이 아니라 FK</b> 집계에서 온다. 이름으로 세면 태그를 개명하는 순간
     * 0 이 된다(할 일이 겪은 QA #79).
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code getTags} 의
     * {@code usage.getOrDefault(tag.getRowId(), 0L)} 를 {@code tag.getTagName()} 키로 바꾸면
     * 맞는 키가 없어 0 이 나와 깨진다.
     */
    @Test
    @DisplayName("getTags — 사용 수는 태그 rowId(FK) 집계에서 온다")
    void usageCountComesFromFkAggregate() {
        given(memoTagRepository.findAllByUser(USER_ID)).willReturn(List.of(ownedTag(7L, "리뷰")));
        given(memoTagRepository.countMemosByTag(USER_ID)).willReturn(Map.of(7L, 3L));

        List<MemoTagServiceDto.TagInfo> infos = sut.getTags(USER_ID);

        assertThat(infos).singleElement()
                .extracting(MemoTagServiceDto.TagInfo::usageCount).isEqualTo(3L);
    }

    @Test
    @DisplayName("getTags — 아무도 안 쓰는 태그는 0 으로 나온다")
    void usageCountDefaultsToZero() {
        given(memoTagRepository.findAllByUser(USER_ID)).willReturn(List.of(ownedTag(7L, "리뷰")));
        given(memoTagRepository.countMemosByTag(USER_ID)).willReturn(Map.of());

        assertThat(sut.getTags(USER_ID)).singleElement()
                .extracting(MemoTagServiceDto.TagInfo::usageCount).isEqualTo(0L);
    }

    // ── tag 문자열 → 태그 확보(메모 저장이 부르는 자리) ─────────────────────

    @Test
    @DisplayName("findOrCreateByName — 같은 이름의 활성 태그가 있으면 그것을 쓰고 새로 만들지 않는다")
    void findOrCreateReusesExisting() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        given(memoTagRepository.findActiveByUserAndName(USER_ID, "업무"))
                .willReturn(Optional.of(ownedTag(9L, "업무")));

        assertThat(sut.findOrCreateByName(USER_ID, "  업무 ")).isEqualTo(9L);
        verify(memoTagRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreateByName — 없으면 만든다(이름은 다듬어서, 색은 비운 채)")
    void findOrCreateCreatesWhenMissing() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        given(memoTagRepository.findActiveByUserAndName(USER_ID, "업무")).willReturn(Optional.empty());
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(memoTagRepository.save(any(MemoTag.class))).willAnswer(inv -> {
            MemoTag t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "rowId", 12L);
            return t;
        });

        assertThat(sut.findOrCreateByName(USER_ID, " 업무")).isEqualTo(12L);

        ArgumentCaptor<MemoTag> captor = ArgumentCaptor.forClass(MemoTag.class);
        verify(memoTagRepository).save(captor.capture());
        assertThat(captor.getValue().getTagName()).isEqualTo("업무");
        assertThat(captor.getValue().getColor()).isNull();
    }

    @Test
    @DisplayName("findOrCreateByName — 빈 이름은 태그를 만들지 않고 400 으로 거절한다")
    void findOrCreateRejectsBlankName() {
        assertThatThrownBy(() -> sut.findOrCreateByName(USER_ID, "  "))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.INVALID_INPUT);
        verify(memoTagRepository, never()).save(any());
    }

    /**
     * 확보 경쟁에서 진 쪽은 <b>상대가 넣은 태그를 다시 찾아 쓴다</b>. 여기서 던지면 태그 하나
     * 때문에 메모 저장 전체가 죽는다. 같은 트랜잭션에서는 재조회가 처음 뜬 스냅샷을 그대로 보므로
     * (REPEATABLE READ) 새 트랜잭션이어야 상대가 커밋한 행이 보인다(#310 실측).
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code findOrCreateByName} 의 {@code catch} 절을
     * 지우면 아래가 {@code DataIntegrityViolationException} 을 맞고 깨진다.
     */
    @Test
    @DisplayName("findOrCreateByName — 유니크 위반은 새 트랜잭션으로 재조회해 그 태그를 쓴다")
    void findOrCreateRetriesOnUniqueViolation() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        given(memoTagRepository.findActiveByUserAndName(USER_ID, "업무"))
                .willReturn(Optional.empty(), Optional.of(ownedTag(33L, "업무")));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        willThrow(ConstraintViolations.unique("UK_memo_tag_user_active_name"))
                .willDoNothing()
                .given(memoTagRepository).flush();

        assertThat(sut.findOrCreateByName(USER_ID, "업무")).isEqualTo(33L);
    }

    @Test
    @DisplayName("findOrCreateByName — 유니크가 아닌 위반은 재시도 없이 그대로 올린다")
    void findOrCreateDoesNotRetryOnOtherViolation() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        given(memoTagRepository.findActiveByUserAndName(USER_ID, "업무")).willReturn(Optional.empty());
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        willThrow(ConstraintViolations.notNull("TAG_NAME")).given(memoTagRepository).flush();

        assertThatThrownBy(() -> sut.findOrCreateByName(USER_ID, "업무"))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(memoTagRepository).findActiveByUserAndName(USER_ID, "업무"); // 재조회 없음 = 1회
    }
}
