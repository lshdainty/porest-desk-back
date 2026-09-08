package com.porest.desk.todo.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.todo.domain.TodoTag;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.repository.TodoTagMappingRepository;
import com.porest.desk.todo.repository.TodoTagRepository;
import com.porest.desk.todo.service.dto.TodoTagServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.support.exception.ConstraintViolations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * 할일 태그 서비스 소유권 가드 회귀 방지 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class TodoTagServiceImplTest {

    @Mock private TodoTagRepository todoTagRepository;
    @Mock private TodoTagMappingRepository todoTagMappingRepository;
    @Mock private TodoRepository todoRepository;
    @Mock private UserRepository userRepository;
    // 생성자 주입 — findOrCreateByName 이 새 트랜잭션 템플릿을 쓰므로 트랜잭션 매니저가 필요하다.
    // 목은 getTransaction/commit 을 무해하게 흘려보내므로 콜백은 그대로 돈다.
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private TodoTagServiceImpl sut;

    private static final long USER_ID = 1L;

    private TodoTag othersTag() {
        User u = User.createUser(null, "x", "남", "x@porest.com");
        ReflectionTestUtils.setField(u, "rowId", 999L);
        TodoTag t = mock(TodoTag.class);
        given(t.getUser()).willReturn(u);
        return t;
    }

    @Test
    @DisplayName("updateTag — 남의 태그는 수정 불가")
    void updateRejectsOthers() {
        TodoTag t = othersTag();
        given(todoTagRepository.findById(5L)).willReturn(Optional.of(t));

        assertThatThrownBy(() -> sut.updateTag(5L, USER_ID, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteTag — 남의 태그는 삭제 불가")
    void deleteRejectsOthers() {
        TodoTag t = othersTag();
        given(todoTagRepository.findById(5L)).willReturn(Optional.of(t));

        assertThatThrownBy(() -> sut.deleteTag(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
        verify(todoRepository, never()).clearCategory(anyLong(), anyString());
        verify(todoTagMappingRepository, never()).deleteByTagId(anyLong());
    }

    /**
     * QA #88 — 태그 행만 지우면 {@code todo.category} 에 이름이 남아, 그 할 일을 다음에 저장할 때
     * 서버가 같은 이름의 태그를 색 없이 새로 만들었다(저장할 때마다 하나씩).
     *
     * <p>삭제 확인창이 "이 태그를 쓰는 할 일 N건은 태그 없음으로 남아요" 라고 약속하므로,
     * 삭제하는 자리에서 그 이름을 비우고 연결도 걷는다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code deleteTag} 에서 {@code clearCategory} ·
     * {@code deleteByTagId} 호출을 지우면(= 종전 동작) 아래 두 단언이 깨진다.
     */
    @Test
    @DisplayName("deleteTag — 그 이름을 쓰던 할일의 category 를 비우고 연결도 걷는다(되살아남 방지)")
    void deleteClearsCategoryAndMappings() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        TodoTag tag = TodoTag.createTag(u, "업무", "#ff0000");
        ReflectionTestUtils.setField(tag, "rowId", 5L);
        given(todoTagRepository.findById(5L)).willReturn(Optional.of(tag));

        sut.deleteTag(5L, USER_ID);

        // 이름은 soft-delete 전 값이어야 한다 — 뒤에서 읽으면 WHERE 가 엉뚱해진다.
        verify(todoRepository).clearCategory(USER_ID, "업무");
        verify(todoTagMappingRepository).deleteByTagId(5L);
    }

    @Test
    @DisplayName("createTag — 활성 태그 중 같은 이름이 있으면 거부(중복 방지)")
    void createRejectsDuplicateActiveName() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(todoTagRepository.existsActiveByUserAndName(USER_ID, "일상", null)).willReturn(true);

        assertThatThrownBy(() -> sut.createTag(new TodoTagServiceDto.CreateCommand(USER_ID, "일상", "#fff")))
                .isInstanceOf(InvalidValueException.class);
    }
    @Test
    @DisplayName("createTag — 이름 앞뒤 공백은 저장 전에 잘린다(선행공백이 DB 판정과 어긋나던 자리)")
    void createTrimsName() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));

        var info = sut.createTag(new TodoTagServiceDto.CreateCommand(USER_ID, "  일상 ", "#fff"));

        assertThat(info.tagName()).isEqualTo("일상");
        verify(todoTagRepository).existsActiveByUserAndName(USER_ID, "일상", null);
    }

    @Test
    @DisplayName("createTag — 빈 이름·공백뿐인 이름은 400 으로 거절한다")
    void createRejectsBlankName() {
        assertThatThrownBy(() -> sut.createTag(new TodoTagServiceDto.CreateCommand(USER_ID, " ", "#fff")))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("createTag — 유니크 위반(동시 저장 경쟁)은 500 이 아니라 409 로 나간다")
    void translatesConstraintViolation() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(todoTagRepository.existsActiveByUserAndName(USER_ID, "일상", null)).willReturn(false);
        willThrow(ConstraintViolations.unique("UK_todo_tag_user_active_name"))
                .given(todoTagRepository).flush();

        assertThatThrownBy(() -> sut.createTag(new TodoTagServiceDto.CreateCommand(USER_ID, "일상", "#fff")))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.TODO_TAG_DUPLICATE_NAME);
    }
    /**
     * QA #81 — <b>UNIQUE 가 아닌 위반은 이 도메인이 손대지 않는다.</b>
     *
     * <p>종전엔 여기서 {@code DataIntegrityViolationException} 을 종류와 무관하게 전부
     * "이름이 중복돼요" 로 번역했다. 그러면 값을 하나 빼먹고 보낸 요청이 <b>있지도 않은
     * 중복</b>을 이유로 거절당한다. 그런 위반은 그대로 올려 공통 핸들러가 400 으로 답하게 둔다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): 서비스의
     * {@code if (!IntegrityViolations.isUnique(e)) throw e;} 한 줄을 지우면 곧바로 깨진다.
     */
    @Test
    @DisplayName("createTag — NOT NULL 위반은 이름 중복으로 번역하지 않고 그대로 올린다")
    void doesNotTranslateNonUniqueViolation() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(todoTagRepository.existsActiveByUserAndName(USER_ID, "일상", null)).willReturn(false);
        willThrow(ConstraintViolations.notNull("COLOR")).given(todoTagRepository).flush();

        assertThatThrownBy(() -> sut.createTag(new TodoTagServiceDto.CreateCommand(USER_ID, "일상", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── QA #79 — 개명해도 옛 이름이 안 남게, 사용 수는 매핑으로 ──────────────────

    private TodoTag ownedTag(long rowId, String name) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        TodoTag t = TodoTag.createTag(u, name, "#fff");
        ReflectionTestUtils.setField(t, "rowId", rowId);
        return t;
    }

    /**
     * QA #79 — 태그를 개명하면 {@code todo.category} 에 남은 옛 이름도 따라 옮긴다.
     *
     * <p><b>이 테스트의 핵심은 WHERE 에 실린 값이다.</b> 옛 이름을 {@code tag.updateTag(...)}
     * <b>뒤에서</b> 읽으면 이미 새 이름이라 {@code renameCategory("회사","회사")} 가 되어
     * 0 행을 고치고 조용히 통과한다 — 그래서 인자를 못 박아 검증한다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code TodoTagServiceImpl.updateTag} 의
     * {@code String previousName = tag.getTagName();} 를 {@code tag.updateTag(...)} 아래로
     * 옮기면 아래가 {@code renameCategory(1, "회사", "회사")} 를 보고 깨진다.
     */
    @Test
    @DisplayName("updateTag — 개명하면 옛 이름을 쓰던 할 일의 category 도 새 이름으로 옮긴다")
    void renameMovesTodoCategory() {
        TodoTag tag = ownedTag(5L, "업무");
        given(todoTagRepository.findById(5L)).willReturn(Optional.of(tag));
        given(todoTagRepository.existsActiveByUserAndName(USER_ID, "회사", 5L)).willReturn(false);

        sut.updateTag(5L, USER_ID, new TodoTagServiceDto.UpdateCommand("회사", "#fff"));

        verify(todoRepository).renameCategory(USER_ID, "업무", "회사");
    }

    @Test
    @DisplayName("updateTag — 이름이 그대로면(색만 바꿔도) 할 일 카테고리는 건드리지 않는다")
    void colorOnlyUpdateLeavesTodosAlone() {
        TodoTag tag = ownedTag(5L, "업무");
        given(todoTagRepository.findById(5L)).willReturn(Optional.of(tag));
        given(todoTagRepository.existsActiveByUserAndName(USER_ID, "업무", 5L)).willReturn(false);

        sut.updateTag(5L, USER_ID, new TodoTagServiceDto.UpdateCommand("업무", "#000"));

        verify(todoRepository, never()).renameCategory(anyLong(), anyString(), anyString());
    }

    /**
     * QA #79 — 사용 수를 <b>이름이 아니라 매핑(FK)</b>으로 센다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code getTags} 를
     * {@code todoRepository.countByCategory} + {@code usage.getOrDefault(tag.getTagName(), 0L)} 로
     * 되돌리면 tagName 키가 없어 0 이 나와 깨진다.
     */
    @Test
    @DisplayName("getTags — 사용 수는 태그 rowId(FK) 집계에서 온다")
    void usageCountComesFromMappingAggregate() {
        TodoTag tag = ownedTag(7L, "리뷰");
        given(todoTagRepository.findAllByUser(USER_ID)).willReturn(List.of(tag));
        given(todoTagRepository.countTodosByTag(USER_ID)).willReturn(Map.of(7L, 3L));

        List<TodoTagServiceDto.TagInfo> infos = sut.getTags(USER_ID);

        assertThat(infos).singleElement()
                .extracting(TodoTagServiceDto.TagInfo::usageCount).isEqualTo(3L);
    }

    // ── category → 태그 확보(할 일 저장이 부르는 자리) ─────────────────────────

    @Test
    @DisplayName("findOrCreateByName — 같은 이름의 활성 태그가 있으면 그것을 쓰고 새로 만들지 않는다")
    void findOrCreateReusesExisting() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        given(todoTagRepository.findActiveByUserAndName(USER_ID, "업무"))
                .willReturn(Optional.of(ownedTag(9L, "업무")));

        // 아이디만이 아니라 이름까지 함께 온다 — 부르는 쪽이 프록시에서 이름을 읽지 않게 하는 값이다(QA #102).
        assertThat(sut.findOrCreateByName(USER_ID, "  업무 "))
                .isEqualTo(new TodoTagServiceDto.TagRef(9L, "업무", "#fff"));
        verify(todoTagRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreateByName — 없으면 만든다(이름은 다듬어서, 색은 비운 채)")
    void findOrCreateCreatesWhenMissing() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        given(todoTagRepository.findActiveByUserAndName(USER_ID, "업무")).willReturn(Optional.empty());
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(todoTagRepository.save(any(TodoTag.class))).willAnswer(inv -> {
            TodoTag t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "rowId", 12L);
            return t;
        });

        // 새로 만든 태그도 아이디·이름을 함께 돌려준다 — 이 트랜잭션 밖에서는 다시 못 읽는다(QA #102).
        assertThat(sut.findOrCreateByName(USER_ID, " 업무"))
                .isEqualTo(new TodoTagServiceDto.TagRef(12L, "업무", null));

        ArgumentCaptor<TodoTag> captor = ArgumentCaptor.forClass(TodoTag.class);
        verify(todoTagRepository).save(captor.capture());
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
        verify(todoTagRepository, never()).save(any());
    }

    /**
     * QA #79 — 확보 경쟁에서 진 쪽은 <b>상대가 넣은 태그를 다시 찾아 쓴다</b>. 여기서 던지면
     * 태그 하나 때문에 할 일 저장 전체가 죽는다(#311 이 가져오기에서 쓴 모양 그대로).
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code findOrCreateByName} 의 {@code catch} 절을
     * 지우면 아래가 {@code DataIntegrityViolationException} 을 맞고 깨진다.
     */
    @Test
    @DisplayName("findOrCreateByName — 유니크 위반은 새 트랜잭션으로 재조회해 그 태그를 쓴다")
    void findOrCreateRetriesOnUniqueViolation() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        given(todoTagRepository.findActiveByUserAndName(USER_ID, "업무"))
                .willReturn(Optional.empty(), Optional.of(ownedTag(33L, "업무")));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        willThrow(ConstraintViolations.unique("UK_todo_tag_user_active_name"))
                .willDoNothing()
                .given(todoTagRepository).flush();

        assertThat(sut.findOrCreateByName(USER_ID, "업무").rowId()).isEqualTo(33L);
    }

    @Test
    @DisplayName("findOrCreateByName — 유니크가 아닌 위반은 재시도 없이 그대로 올린다")
    void findOrCreateDoesNotRetryOnOtherViolation() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        given(todoTagRepository.findActiveByUserAndName(USER_ID, "업무")).willReturn(Optional.empty());
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        willThrow(ConstraintViolations.notNull("TAG_NAME")).given(todoTagRepository).flush();

        assertThatThrownBy(() -> sut.findOrCreateByName(USER_ID, "업무"))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(todoTagRepository).findActiveByUserAndName(USER_ID, "업무"); // 재조회 없음 = 1회
    }
}
