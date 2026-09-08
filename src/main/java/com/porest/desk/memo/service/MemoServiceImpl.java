package com.porest.desk.memo.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.type.YNType;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.common.patch.Patch;
import com.porest.desk.constellation.service.StarlightService;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.memo.domain.MemoTag;
import com.porest.desk.memo.repository.MemoRepository;
import com.porest.desk.memo.repository.MemoTagRepository;
import com.porest.desk.memo.service.dto.MemoServiceDto;
import com.porest.desk.memo.service.dto.MemoTagServiceDto;
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
public class MemoServiceImpl implements MemoService {
    private final MemoRepository memoRepository;
    private final MemoTagRepository memoTagRepository;
    private final MemoTagService memoTagService;
    private final UserRepository userRepository;
    private final StarlightService starlightService;

    @Override
    @Transactional
    public MemoServiceDto.MemoInfo createMemo(MemoServiceDto.CreateCommand command) {
        log.debug("메모 등록 시작: userRowId={}, title={}", command.userRowId(), command.title());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        // 태그 확보를 메모 INSERT 앞에 둔다 — 확보는 새 트랜잭션에서 돌므로(findOrCreateByName)
        // 이 트랜잭션에 못 내보낸 변경이 없는 자리에서 끝내는 편이 안전하다(할 일과 같은 순서).
        TagLink link = resolveTagLink(command.memoTagRowId(), command.tag(),
            command.userRowId(), null);

        Memo memo = Memo.createMemo(user, command.title(), command.content(),
            link.tagName(), link.tag(), command.color());

        memoRepository.save(memo);
        // 별자리 게이미피케이션 — 기록하면 오늘의 별빛이 된다 (+1, 일 최대 2)
        starlightService.onMemoCreated(memo);
        log.info("메모 등록 완료: memoId={}, userRowId={}", memo.getRowId(), command.userRowId());

        // 응답의 memoTagRowId 는 memo.getMemoTag() 가 아니라 link 에서 꺼낸다 — 방금 이은 것이
        // 프록시라 아이디를 읽는 것만으로 SELECT 가 나가고, 처음 쓰는 이름이면 그 SELECT 가
        // 빈손으로 돌아온다(QA #102).
        return MemoServiceDto.MemoInfo.from(memo, link.tagRowId());
    }

    @Override
    public List<MemoServiceDto.MemoInfo> getMemos(Long userRowId, String search) {
        log.debug("메모 목록 조회: userRowId={}, search={}", userRowId, search);

        List<Memo> memos = memoRepository.findAllByUser(userRowId, search);

        return memos.stream()
            .map(MemoServiceDto.MemoInfo::from)
            .toList();
    }

    @Override
    public MemoServiceDto.MemoInfo getMemo(Long memoId, Long userRowId) {
        log.debug("메모 상세 조회: memoId={}", memoId);

        Memo memo = findMemoOrThrow(memoId);
        validateMemoOwnership(memo, userRowId);

        return MemoServiceDto.MemoInfo.from(memo);
    }

    @Override
    @Transactional
    public MemoServiceDto.MemoInfo updateMemo(Long memoId, Long userRowId, MemoServiceDto.UpdateCommand command) {
        log.debug("메모 수정 시작: memoId={}", memoId);

        Memo memo = findMemoOrThrow(memoId);
        validateMemoOwnership(memo, userRowId);

        // ★ 태그 확보를 memo.updateMemo(...) 앞에서 끝낸다 — 덮은 뒤에 읽으면 "지금 붙은 태그"가
        //   이미 새 값이라 아래 지름길(같은 이름이면 그대로)이 늘 맞는다고 답한다.
        //   확보가 새 트랜잭션을 여는 것도 이 트랜잭션에 못 내보낸 변경이 없을 때가 안전하다.
        TagLink link = resolveUpdatedTagLink(command, memo, userRowId);

        // 실린 칸만 바꾼다 — 안 온 칸은 지금 값을 그대로 넘긴다(QA #96).
        memo.updateMemo(
            command.title().orKeep(memo.getTitle()),
            command.content().orKeep(memo.getContent()),
            link.tagName(), link.tag(),
            command.color().orKeep(memo.getColor()));

        log.info("메모 수정 완료: memoId={}", memoId);

        // 등록과 같은 이유로 link 에서 꺼낸다(QA #102).
        return MemoServiceDto.MemoInfo.from(memo, link.tagRowId());
    }

    @Override
    @Transactional
    public MemoServiceDto.MemoInfo togglePin(Long memoId, Long userRowId) {
        log.debug("메모 핀 토글 시작: memoId={}", memoId);

        Memo memo = findMemoOrThrow(memoId);
        validateMemoOwnership(memo, userRowId);
        memo.togglePin();

        log.info("메모 핀 토글 완료: memoId={}, isPinned={}", memoId, memo.getIsPinned());

        return MemoServiceDto.MemoInfo.from(memo);
    }

    @Override
    @Transactional
    public void deleteMemo(Long memoId, Long userRowId) {
        log.debug("메모 삭제 시작: memoId={}", memoId);

        Memo memo = findMemoOrThrow(memoId);
        validateMemoOwnership(memo, userRowId);
        memo.deleteMemo();
        // 별자리 게이미피케이션 — 당일 적립분이면 별빛 회수
        starlightService.onMemoDeleted(memo);

        log.info("메모 삭제 완료: memoId={}", memoId);
    }

    // ── 태그 다리 ────────────────────────────────────────────────────────────
    // 웹·앱 어느 쪽도 태그 아이디를 안 보낸다 — 보내는 것은 memo.tag 문자열 하나다(2026-09-08
    // 양쪽 코드로 확인: 웹 MemoPage 의 TAG_OPTIONS 7종, 앱 memo_edit_dialog 의 kMemoTags 7종이
    // 각각 하드코딩돼 있다). 그래서 마스터 테이블만 만들면 FK 가 영영 비어 있다.
    // 서버가 여기서 다리를 놓아 "그 문자열로 활성 태그를 찾고, 없으면 만들어서" FK 를 채운다 —
    // 클라이언트는 한 줄도 안 고쳐도 되고, 옛 메모는 한 번 저장될 때 채워진다(할 일 #314 와 같은 판단).

    /**
     * {@code memo.tag} 문자열과 {@code memo.memoTag} FK 한 쌍 — 둘은 늘 함께 정해진다.
     *
     * <p>{@code tag} 가 null 이면 태그 없음이다. {@code tag} 는 있는데 {@code memoTag} 가
     * null 인 조합은 <b>확보에 실패한 경우</b>다 — 사용자가 친 글자는 지키고(잃으면 안 된다)
     * 다음 저장에서 다시 잇는다.
     */
    private record TagLink(String tagName, MemoTag tag, Long tagRowId) {
        private static final TagLink NONE = new TagLink(null, null, null);

        /** 이미 읽은 마스터로 잇는다 — 조회로 얻은 엔티티라 필드를 읽어도 안전하다. */
        private static TagLink of(MemoTag tag) {
            return new TagLink(tag.getTagName(), tag, tag.getRowId());
        }
    }

    /**
     * 수정에서 남길 태그 — {@code memoTagRowId} 가 <b>본문에 실렸는지</b>가 먼저다(QA #96 · #321).
     *
     * <p>키가 아예 없으면 아이디를 안 고친다는 뜻이므로 {@code tag} 문자열(병합된 값)로 잇는다.
     * {@code "memoTagRowId": null} 은 <b>태그를 뗀다</b>는 뜻이라 문자열까지 함께 비운다 —
     * 문자열만 남기면 다음 저장에서 그 이름의 태그가 되살아난다(QA #88).
     * {@code "tag": null} 도 같은 결과다(병합 값이 null 이 되어 아래 {@code linkByName} 이 NONE 을 돌려준다).
     */
    private TagLink resolveUpdatedTagLink(MemoServiceDto.UpdateCommand command, Memo memo, Long userRowId) {
        Patch<Long> tagId = command.memoTagRowId();
        if (tagId.present()) {
            return tagId.value() == null ? TagLink.NONE : linkByTagId(tagId.value(), userRowId);
        }
        return linkByName(command.tag().orKeep(memo.getTag()), userRowId, memo.getMemoTag());
    }

    /**
     * 이번 저장에서 남길 태그를 정한다 — <b>명시한 아이디가 있으면 그쪽이 이긴다.</b>
     *
     * <p>옛 클라이언트가 보내는 문자열이 새 클라이언트가 고른 마스터를 덮으면 안 되기 때문이다.
     * 아이디를 실었으면 {@code memo.tag} 도 그 마스터의 이름으로 맞춘다 — 두 값이 갈리면
     * 이 PR 이 없애려던 상태(이름과 마스터가 서로 다른 태그를 가리킴)를 도로 만든다.
     *
     * @param explicitTagRowId 본문이 지목한 태그 아이디(없으면 null)
     * @param tagText          병합이 끝난 {@code tag} 문자열(없으면 null)
     * @param currentTag       지금 이 메모에 붙어 있는 마스터(등록이면 null)
     */
    private TagLink resolveTagLink(Long explicitTagRowId, String tagText, Long userRowId, MemoTag currentTag) {
        if (explicitTagRowId != null) {
            return linkByTagId(explicitTagRowId, userRowId);
        }
        return linkByName(tagText, userRowId, currentTag);
    }

    /**
     * 요청이 지목한 태그를 <b>내 것인지 확인하고</b> 돌려준다 — 없으면 404, 남의 것이면 403.
     *
     * <p>할 일이 이 확인을 빠뜨려 남의 태그 이름·색이 내 응답으로 나갔다(QA #79 — 응답 유출).
     * 메모는 처음부터 건다.
     */
    private TagLink linkByTagId(Long tagRowId, Long userRowId) {
        MemoTag tag = memoTagRepository.findById(tagRowId)
            .orElseThrow(() -> {
                log.warn("메모 태그 조회 실패 - 존재하지 않거나 삭제된 태그: tagId={}", tagRowId);
                return new EntityNotFoundException(DeskErrorCode.MEMO_TAG_NOT_FOUND);
            });
        validateTagOwnership(tag, userRowId);
        return TagLink.of(tag);
    }

    /** 문자열 → 그 사용자의 활성 태그(없으면 만든다). 빈 문자열은 태그를 만들지 않는다. */
    private TagLink linkByName(String tagText, Long userRowId, MemoTag currentTag) {
        String name = blankToNull(tagText);
        if (name == null) return TagLink.NONE;

        // 지름길 — 이미 그 이름의 살아 있는 마스터가 붙어 있으면 확보를 건너뛴다.
        // 확보는 새 트랜잭션을 여느라 지금 트랜잭션을 잠깐 멈추므로, 본문만 고치는 흔한 저장에서
        // 그 값을 치르지 않게 한다. 삭제된 태그면 지름길을 쓰지 않는다 — 그때는 다시 확보해야 한다.
        if (currentTag != null && currentTag.getIsDeleted() == YNType.N
                && name.equals(currentTag.getTagName())) {
            return TagLink.of(currentTag);
        }

        // ★ 확보가 돌려준 아이디를 findById 로 다시 읽지 마라(QA #102). 확보는 새 트랜잭션에서
        //   커밋하는데 이 트랜잭션은 그 앞에서 스냅샷을 잡았으므로 처음 쓰는 이름은 안 보이고,
        //   FK 가 빈 채로 저장돼 사용 수가 0 이 된다. 프록시는 조회를 안 하니 스냅샷과 무관하고
        //   행은 이미 커밋돼 있어 FK 삽입은 통과한다.
        MemoTagServiceDto.TagRef ref = memoTagService.findOrCreateByName(userRowId, name);
        // 확보에 실패해도 사용자가 친 글자는 그대로 남긴다 — 다음 저장에서 다시 잇는다.
        if (ref == null) return new TagLink(name, null, null);
        // 이름은 마스터를 따른다: 콜레이션(utf8mb4_unicode_ci)이 "Food" 와 "food" 를 같은 이름으로
        // 보므로, 확보된 마스터의 표기로 통일해야 개명·삭제의 WHERE 가 이 행을 정확히 찾는다.
        // 그 표기는 확보가 실어 보낸 값이다 — 프록시에서 읽으면 지연 로딩 SELECT 가 나가
        // 같은 스냅샷에 부딪혀 EntityNotFoundException 이 된다.
        return new TagLink(ref.tagName(), memoTagRepository.getReference(ref.rowId()), ref.rowId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateTagOwnership(MemoTag tag, Long userRowId) {
        Long ownerRowId = tag.getUser() != null ? tag.getUser().getRowId() : null;
        if (!userRowId.equals(ownerRowId)) {
            log.warn("메모 태그 소유권 검증 실패 - tagId={}, ownerRowId={}, requestUserRowId={}",
                tag.getRowId(), ownerRowId, userRowId);
            throw new ForbiddenException(DeskErrorCode.MEMO_ACCESS_DENIED);
        }
    }

    private void validateMemoOwnership(Memo memo, Long userRowId) {
        if (!memo.getUser().getRowId().equals(userRowId)) {
            log.warn("메모 소유권 검증 실패 - memoId={}, ownerRowId={}, requestUserRowId={}",
                memo.getRowId(), memo.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.MEMO_ACCESS_DENIED);
        }
    }

    private Memo findMemoOrThrow(Long memoId) {
        return memoRepository.findById(memoId)
            .orElseThrow(() -> {
                log.warn("메모 조회 실패 - 존재하지 않는 메모: memoId={}", memoId);
                return new EntityNotFoundException(DeskErrorCode.MEMO_NOT_FOUND);
            });
    }
}
