package com.porest.desk.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 해지한 사람의 이름은 <b>남에게 보이지 않는다.</b>
 *
 * <p>해지해도 남의 캘린더·정산에 남긴 흔적(일정·댓글·참가)은 그대로 둔다 — 그건 그
 * 사람들의 기록이라 지울 것이 아니다. 대신 이름은 더 보이면 안 된다.
 *
 * <p>조회하는 자리마다 각자 판단하면 <b>한 곳을 빠뜨리는 순간 거기서만 실명이 남는다</b>
 * (2026-09-17 QA #10: 댓글 작성자가 그랬다). 그래서 엔티티가 한 번에 정하고, 여기서
 * 그 규칙을 못박는다.
 */
@DisplayName("남에게 보일 이름")
class UserDisplayNameTest {

    private User user() {
        return User.createUser(100L, "tester", "김테스터", "t@porest.com");
    }

    @Test
    @DisplayName("평소에는 본인 이름 그대로")
    void showsRealNameWhileActive() {
        assertThat(user().displayName()).isEqualTo("김테스터");
    }

    @Test
    @DisplayName("해지하면 '탈퇴한 사용자' 로 바뀐다")
    void hidesNameAfterWithdrawal() {
        User u = user();
        u.withdraw("그냥");

        assertThat(u.displayName()).isEqualTo(User.WITHDRAWN_DISPLAY_NAME);
        // 저장된 이름 자체는 남는다 — 본인 확인·감사 기록에 쓰인다. 가리는 것은 표시다.
        assertThat(u.getUserName()).isEqualTo("김테스터");
    }

    @Test
    @DisplayName("정산 익명화 이름과 같은 말을 쓴다 — 갈리면 한 사람이 두 이름으로 보인다")
    void sameWordAsAnonymizedParticipant() {
        assertThat(User.WITHDRAWN_DISPLAY_NAME).isEqualTo("탈퇴한 사용자");
    }
}
