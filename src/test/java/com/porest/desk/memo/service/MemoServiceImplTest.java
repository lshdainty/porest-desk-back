package com.porest.desk.memo.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.type.YNType;
import com.porest.desk.common.patch.Patch;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.memo.domain.MemoTag;
import com.porest.desk.memo.repository.MemoRepository;
import com.porest.desk.memo.repository.MemoTagRepository;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.porest.desk.memo.service.dto.MemoServiceDto;
import com.porest.desk.memo.service.dto.MemoTagServiceDto;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 메모 서비스 소유권 가드 회귀 방지 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class MemoServiceImplTest {

    @Mock private MemoRepository memoRepository;
    @Mock private MemoTagRepository memoTagRepository;
    @Mock private MemoTagService memoTagService;
    @Mock private UserRepository userRepository;
    @Mock private com.porest.desk.constellation.service.StarlightService starlightService;

    @InjectMocks private MemoServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private MemoTag tag(long rowId, long ownerRowId, String name) {
        MemoTag t = MemoTag.createTag(user(ownerRowId), name, "#123456");
        ReflectionTestUtils.setField(t, "rowId", rowId);
        return t;
    }

    /**
     * {@code tag} 문자열 → 마스터 확보가 성공하는 상황을 만든다.
     *
     * <p>확보는 아이디만이 아니라 <b>이름·색까지</b> 돌려주고, 부르는 쪽은 그 아이디로
     * {@code getReference} 참조만 잡는다 — {@code findById} 는 부르지 않는다(QA #102).
     */
    private MemoTag stubResolve(String name, long tagRowId) {
        MemoTag t = tag(tagRowId, USER_ID, name);
        given(memoTagService.findOrCreateByName(USER_ID, name))
                .willReturn(MemoTagServiceDto.TagRef.from(t));
        given(memoTagRepository.getReference(tagRowId)).willReturn(t);
        return t;
    }

    private Memo othersMemo() {
        Memo m = mock(Memo.class);
        given(m.getUser()).willReturn(user(999L));
        return m;
    }

    @Test
    @DisplayName("getMemo — 남의 메모는 조회 불가")
    void getRejectsOthers() {
        Memo m = othersMemo();
        given(memoRepository.findById(5L)).willReturn(Optional.of(m));

        assertThatThrownBy(() -> sut.getMemo(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("togglePin — 남의 메모는 고정 불가")
    void togglePinRejectsOthers() {
        Memo m = othersMemo();
        given(memoRepository.findById(5L)).willReturn(Optional.of(m));

        assertThatThrownBy(() -> sut.togglePin(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteMemo — 남의 메모는 삭제 불가")
    void deleteRejectsOthers() {
        Memo m = othersMemo();
        given(memoRepository.findById(5L)).willReturn(Optional.of(m));

        assertThatThrownBy(() -> sut.deleteMemo(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── 정상 CRUD 결과 정확성 ─────────────────────────────
    @Test
    @DisplayName("createMemo — isPinned=N·필드 1:1 매핑")
    void createMemoDefaults() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new MemoServiceDto.CreateCommand(USER_ID, "회의록", "본문", "work", null, "#FF0000");
        MemoServiceDto.MemoInfo info = sut.createMemo(cmd);

        assertThat(info.userRowId()).isEqualTo(USER_ID);
        assertThat(info.title()).isEqualTo("회의록");
        assertThat(info.content()).isEqualTo("본문");
        assertThat(info.tag()).isEqualTo("work");
        assertThat(info.color()).isEqualTo("#FF0000");
        assertThat(info.isPinned()).isEqualTo(YNType.N);
    }

    @Test
    @DisplayName("togglePin — N→Y, 한 번 더 Y→N")
    void togglePinFlips() {
        Memo memo = Memo.createMemo(user(USER_ID), "old", "body", "tag", null, "#000000");
        ReflectionTestUtils.setField(memo, "rowId", 202L);
        given(memoRepository.findById(202L)).willReturn(Optional.of(memo));

        assertThat(sut.togglePin(202L, USER_ID).isPinned()).isEqualTo(YNType.Y); // N→Y
        assertThat(sut.togglePin(202L, USER_ID).isPinned()).isEqualTo(YNType.N); // Y→N
    }

    /**
     * QA #81 — 수정에서 {@code title} 이 빠지면 <b>기존 제목을 지킨다</b>. {@code memo.title} 은
     * NOT NULL 이라 종전엔 null 을 덮어써 409 "다른 곳에서 먼저 수정됐어요" 로 튕겼다.
     *
     * <p>지금은 DTO 의 {@code @NotBlank} 가 HTTP 앞단에서 먼저 끊고, 그 앞에 "안 실린 칸은
     * 기존 값" 병합이 있다(QA #96). 그래도 이 자리를 남긴다 — 검증은 애노테이션 하나가
     * 지워지는 순간 사라지고, 엔티티가 자기 NOT NULL 을 지키는 쪽이 남는다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code MemoServiceImpl} 의 {@code orKeep(...)} 을
     * {@code command.title().value()} 로 바꾸면 아래가 null 제목을 만나 깨진다.
     */
    @Test
    @DisplayName("updateMemo — title 이 없으면 기존 제목을 지킨다")
    void updateKeepsExistingTitleWhenAbsent() {
        Memo memo = Memo.createMemo(user(USER_ID), "원제목", "본문", null, null, null);
        ReflectionTestUtils.setField(memo, "rowId", 5L);
        given(memoRepository.findById(5L)).willReturn(Optional.of(memo));

        var info = sut.updateMemo(5L, USER_ID, new MemoServiceDto.UpdateCommand(
                Patch.absent(), Patch.set("고친본문"), Patch.absent(), Patch.absent(), Patch.absent()));

        assertThat(info.title()).isEqualTo("원제목");
        assertThat(info.content()).isEqualTo("고친본문");
    }

    // ── 태그 다리 (QA #98) ─────────────────────────────────────────────────
    // 웹·앱은 태그 아이디를 안 보낸다 — memo.tag 문자열 하나뿐이다. 그래서 마스터 테이블만
    // 만들면 FK 가 영영 빈다. 아래가 "문자열로 확보해 FK 를 채운다" 를 붙들어 두는 자리다.

    /**
     * 되돌려 보는 법(네거티브 컨트롤): {@code MemoServiceImpl.createMemo} 의
     * {@code link.tag()} 를 {@code null} 로 바꾸면 FK 단언이 깨진다.
     */
    @Test
    @DisplayName("createMemo — tag 문자열로 마스터를 확보해 FK 를 잇는다")
    void createBridgesTagStringToMaster() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        MemoTag master = stubResolve("업무", 11L);

        var info = sut.createMemo(new MemoServiceDto.CreateCommand(
                USER_ID, "회의록", "본문", "업무", null, "#FF0000"));

        assertThat(info.memoTagRowId()).isEqualTo(11L);
        assertThat(info.tag()).isEqualTo(master.getTagName());
    }

    @Test
    @DisplayName("createMemo — 빈 tag 는 태그를 만들지 않는다")
    void createDoesNotCreateTagForBlank() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var info = sut.createMemo(new MemoServiceDto.CreateCommand(
                USER_ID, "회의록", "본문", "   ", null, null));

        assertThat(info.tag()).isNull();
        assertThat(info.memoTagRowId()).isNull();
        verify(memoTagService, never()).findOrCreateByName(anyLong(), anyString());
    }

    /**
     * 이름은 <b>마스터를 따른다</b>. 콜레이션(utf8mb4_unicode_ci)이 "Food" 와 "food" 를 같은
     * 이름으로 보므로, 확보된 마스터의 표기로 통일해야 개명·삭제의 WHERE 가 이 행을 찾는다.
     *
     * <p>되돌려 보는 법: {@code linkByName} 의 마지막 줄에서 {@code ref.tagName()} 대신
     * {@code name} 을 넘기면(마스터 표기 대신 사용자가 친 글자를 남기면) 아래가 깨진다.
     */
    @Test
    @DisplayName("createMemo — 저장되는 tag 문자열은 마스터의 표기를 따른다")
    void createNormalizesTagTextToMasterName() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        MemoTag master = tag(11L, USER_ID, "Food");
        given(memoTagService.findOrCreateByName(USER_ID, "food"))
                .willReturn(MemoTagServiceDto.TagRef.from(master));
        given(memoTagRepository.getReference(11L)).willReturn(master);

        var info = sut.createMemo(new MemoServiceDto.CreateCommand(
                USER_ID, "회의록", null, "food", null, null));

        assertThat(info.tag()).isEqualTo("Food");
        assertThat(info.memoTagRowId()).isEqualTo(11L);
    }

    /**
     * QA #102 — <b>처음 쓰는 이름</b>. 확보({@code findOrCreateByName})는 새 트랜잭션에서
     * 커밋하는데, 저장 트랜잭션은 그 앞에서 스냅샷을 잡았으므로 MariaDB 기본 격리수준
     * (REPEATABLE READ)에서 그 행이 안 보인다. 종전 코드는 그 아이디를 {@code findById} 로
     * 다시 읽어 {@code orElse(null)} 에 떨어졌고, 마스터만 생기고 FK 는 빈 채로 저장됐다
     * (설정의 사용 수 0 · 필터에서 누락). 이제 다시 읽지 않고 참조로 잇는다.
     *
     * <p>여기서 {@code findById} 를 스텁하지 않은 것이 곧 그 상황이다 — 못 읽는다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code linkByName} 의 마지막 줄을
     * {@code memoTagRepository.findById(ref.rowId()).orElse(null)} 로 되돌리면
     * {@code memoTagRowId} 가 null 이 되어 깨진다.
     */
    @Test
    @DisplayName("createMemo — 처음 쓰는 이름도 다시 읽지 않고(참조로) FK 를 잇는다")
    void createLinksBrandNewTagWithoutRereading() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        MemoTag master = stubResolve("신규", 11L);

        var info = sut.createMemo(new MemoServiceDto.CreateCommand(
                USER_ID, "회의록", "본문", "신규", null, null));

        assertThat(info.memoTagRowId()).isEqualTo(11L);
        assertThat(info.tag()).isEqualTo("신규");
        verify(memoTagRepository, never()).findById(anyLong());

        ArgumentCaptor<Memo> saved = ArgumentCaptor.forClass(Memo.class);
        verify(memoRepository).save(saved.capture());
        assertThat(saved.getValue().getMemoTag()).isSameAs(master);
    }

    /**
     * 응답의 {@code memoTagRowId} 는 <b>메모에 붙은 마스터에서 읽지 않는다</b> — 방금 붙인 것은
     * 조회를 안 한 참조라, 아이디를 읽는 것만으로 지연 로딩 SELECT 가 나가고 그 SELECT 는
     * 스냅샷에 없는 행을 찾아 {@code EntityNotFoundException} 이 된다(QA #102 · 리포 슬라이스
     * 테스트가 그 동작을 못 박아 뒀다). 확보가 실어 보낸 아이디를 그대로 싣는다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code createMemo} 의 마지막 줄을
     * {@code MemoServiceDto.MemoInfo.from(memo)} 로 되돌리면 아래가 예외로 깨진다.
     */
    @Test
    @DisplayName("createMemo — 응답을 만들 때 방금 이은 참조를 건드리지 않는다")
    void createDoesNotTouchTheProxyWhenBuildingResponse() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        // 초기화되지 않은 참조를 흉내 낸다 — 건드리면 터진다. lenient 인 이유는
        // "안 불린다" 가 이 테스트의 결론이기 때문이다.
        MemoTag unreadable = mock(MemoTag.class);
        lenient().when(unreadable.getRowId())
                .thenThrow(new jakarta.persistence.EntityNotFoundException("초기화되지 않은 참조"));
        given(memoTagService.findOrCreateByName(USER_ID, "신규"))
                .willReturn(new MemoTagServiceDto.TagRef(11L, "신규", null));
        given(memoTagRepository.getReference(11L)).willReturn(unreadable);

        var info = sut.createMemo(new MemoServiceDto.CreateCommand(
                USER_ID, "회의록", null, "신규", null, null));

        assertThat(info.memoTagRowId()).isEqualTo(11L);
        verify(unreadable, never()).getRowId();
    }

    /**
     * 확보가 실패해도(경쟁 뒤 재조회까지 못 찾은 경우) <b>사용자가 친 글자는 지킨다</b> —
     * 잃으면 되살릴 방법이 없고, 다음 저장에서 다시 이으면 된다.
     */
    @Test
    @DisplayName("createMemo — 확보에 실패해도 tag 문자열은 남는다")
    void createKeepsTextWhenResolveFails() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(memoTagService.findOrCreateByName(USER_ID, "업무")).willReturn(null);

        var info = sut.createMemo(new MemoServiceDto.CreateCommand(
                USER_ID, "회의록", null, "업무", null, null));

        assertThat(info.tag()).isEqualTo("업무");
        assertThat(info.memoTagRowId()).isNull();
    }

    /**
     * QA #98 — 옛 메모는 FK 가 비어 있다. {@code tag} 키를 안 보내는 저장(본문만 고치기)에서도
     * 지금 값으로 확보가 돌아 FK 가 채워져야 한다. 그래야 백필 없이도 조금씩 찬다.
     *
     * <p>되돌려 보는 법: {@code updateMemo} 에서 {@code resolveUpdatedTagLink(...)} 대신
     * {@code memo.getMemoTag()} 를 그대로 넘기면 FK 가 null 로 남아 깨진다.
     */
    @Test
    @DisplayName("updateMemo — tag 키가 없어도 지금 이름으로 FK 를 채운다")
    void updateBackfillsFkWhenTagAbsent() {
        Memo memo = Memo.createMemo(user(USER_ID), "제목", "본문", "업무", null, null);
        ReflectionTestUtils.setField(memo, "rowId", 5L);
        given(memoRepository.findById(5L)).willReturn(Optional.of(memo));
        stubResolve("업무", 11L);

        var info = sut.updateMemo(5L, USER_ID, new MemoServiceDto.UpdateCommand(
                Patch.absent(), Patch.set("고친본문"), Patch.absent(), Patch.absent(), Patch.absent()));

        assertThat(info.tag()).isEqualTo("업무");
        assertThat(info.memoTagRowId()).isEqualTo(11L);
    }

    /**
     * 지름길 — 이미 같은 이름의 살아 있는 마스터가 붙어 있으면 확보를 건너뛴다.
     * 확보는 새 트랜잭션을 여느라 지금 트랜잭션을 멈추므로, 본문만 고치는 흔한 저장에서 그 값을
     * 치르지 않게 한다.
     */
    @Test
    @DisplayName("updateMemo — 이미 같은 이름 마스터가 붙어 있으면 확보를 다시 하지 않는다")
    void updateSkipsResolveWhenAlreadyLinked() {
        MemoTag master = tag(11L, USER_ID, "업무");
        Memo memo = Memo.createMemo(user(USER_ID), "제목", "본문", "업무", master, null);
        ReflectionTestUtils.setField(memo, "rowId", 5L);
        given(memoRepository.findById(5L)).willReturn(Optional.of(memo));

        var info = sut.updateMemo(5L, USER_ID, new MemoServiceDto.UpdateCommand(
                Patch.absent(), Patch.set("고친본문"), Patch.absent(), Patch.absent(), Patch.absent()));

        assertThat(info.memoTagRowId()).isEqualTo(11L);
        verify(memoTagService, never()).findOrCreateByName(anyLong(), anyString());
    }

    /**
     * 8차 #321 이 정한 PUT 의미 위에 쌓는다 — {@code "tag": null} 은 <b>지운다</b>는 뜻이므로
     * 문자열과 FK 가 함께 떨어져야 한다. 한쪽만 떨어지면 다음 저장에서 되살아난다(QA #88).
     *
     * <p>되돌려 보는 법: {@code resolveUpdatedTagLink} 의 마지막 줄에서
     * {@code command.tag().orKeep(...)} 대신 {@code memo.getTag()} 를 넘기면 깨진다.
     */
    @Test
    @DisplayName("updateMemo — tag 를 명시적 null 로 보내면 문자열과 FK 가 함께 떨어진다")
    void updateDetachesTagOnExplicitNull() {
        MemoTag master = tag(11L, USER_ID, "업무");
        Memo memo = Memo.createMemo(user(USER_ID), "제목", "본문", "업무", master, null);
        ReflectionTestUtils.setField(memo, "rowId", 5L);
        given(memoRepository.findById(5L)).willReturn(Optional.of(memo));

        var info = sut.updateMemo(5L, USER_ID, new MemoServiceDto.UpdateCommand(
                Patch.absent(), Patch.absent(), Patch.set(null), Patch.absent(), Patch.absent()));

        assertThat(info.tag()).isNull();
        assertThat(info.memoTagRowId()).isNull();
        assertThat(memo.getMemoTag()).isNull();
    }

    @Test
    @DisplayName("updateMemo — memoTagRowId 를 명시적 null 로 보내면 문자열까지 함께 비운다")
    void updateDetachesBothOnExplicitNullTagId() {
        MemoTag master = tag(11L, USER_ID, "업무");
        Memo memo = Memo.createMemo(user(USER_ID), "제목", "본문", "업무", master, null);
        ReflectionTestUtils.setField(memo, "rowId", 5L);
        given(memoRepository.findById(5L)).willReturn(Optional.of(memo));

        var info = sut.updateMemo(5L, USER_ID, new MemoServiceDto.UpdateCommand(
                Patch.absent(), Patch.absent(), Patch.absent(), Patch.set(null), Patch.absent()));

        assertThat(info.tag()).isNull();
        assertThat(info.memoTagRowId()).isNull();
        verify(memoTagService, never()).findOrCreateByName(anyLong(), anyString());
    }

    /**
     * 명시한 아이디가 문자열을 이긴다 — 옛 클라이언트가 보내는 문자열이 새 클라이언트가 고른
     * 마스터를 덮으면 안 된다. 이름도 마스터를 따라간다(두 값이 갈리면 이 PR 이 없애려던 상태다).
     */
    @Test
    @DisplayName("updateMemo — memoTagRowId 가 tag 문자열을 이기고 이름도 마스터를 따른다")
    void updateExplicitTagIdWins() {
        Memo memo = Memo.createMemo(user(USER_ID), "제목", "본문", "업무", null, null);
        ReflectionTestUtils.setField(memo, "rowId", 5L);
        given(memoRepository.findById(5L)).willReturn(Optional.of(memo));
        given(memoTagRepository.findById(22L)).willReturn(Optional.of(tag(22L, USER_ID, "개인")));

        var info = sut.updateMemo(5L, USER_ID, new MemoServiceDto.UpdateCommand(
                Patch.absent(), Patch.absent(), Patch.set("업무"), Patch.set(22L), Patch.absent()));

        assertThat(info.memoTagRowId()).isEqualTo(22L);
        assertThat(info.tag()).isEqualTo("개인");
        verify(memoTagService, never()).findOrCreateByName(anyLong(), anyString());
    }

    /**
     * QA #79 가 할 일에서 잡은 응답 유출 — 남의 태그 아이디를 그대로 이으면 그 태그의 이름·색이
     * 내 응답으로 나간다. 메모는 처음부터 403 으로 끊는다.
     *
     * <p>되돌려 보는 법: {@code linkByTagId} 의 {@code validateTagOwnership(tag, userRowId)} 를
     * 지우면 403 이 아니라 200 이 나가고 아래가 깨진다.
     */
    @Test
    @DisplayName("updateMemo — 남의 태그 아이디를 실으면 403")
    void updateRejectsOthersTagId() {
        Memo memo = Memo.createMemo(user(USER_ID), "제목", "본문", null, null, null);
        ReflectionTestUtils.setField(memo, "rowId", 5L);
        given(memoRepository.findById(5L)).willReturn(Optional.of(memo));
        given(memoTagRepository.findById(99L)).willReturn(Optional.of(tag(99L, 999L, "남의태그")));

        assertThatThrownBy(() -> sut.updateMemo(5L, USER_ID, new MemoServiceDto.UpdateCommand(
                Patch.absent(), Patch.absent(), Patch.absent(), Patch.set(99L), Patch.absent())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateMemo — 없거나 삭제된 태그 아이디는 404")
    void updateRejectsMissingTagId() {
        Memo memo = Memo.createMemo(user(USER_ID), "제목", "본문", null, null, null);
        ReflectionTestUtils.setField(memo, "rowId", 5L);
        given(memoRepository.findById(5L)).willReturn(Optional.of(memo));
        given(memoTagRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.updateMemo(5L, USER_ID, new MemoServiceDto.UpdateCommand(
                Patch.absent(), Patch.absent(), Patch.absent(), Patch.set(404L), Patch.absent())))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("createMemo — 남의 태그 아이디를 실으면 403")
    void createRejectsOthersTagId() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(memoTagRepository.findById(99L)).willReturn(Optional.of(tag(99L, 999L, "남의태그")));

        assertThatThrownBy(() -> sut.createMemo(new MemoServiceDto.CreateCommand(
                USER_ID, "제목", null, null, 99L, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(memoRepository, never()).save(any(Memo.class));
    }
}
