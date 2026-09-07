package com.porest.desk.common.patch;

import java.util.Optional;
import java.util.function.Function;

/**
 * PUT 본문의 한 칸이 <b>어떤 상태로 왔는지</b>를 그대로 옮기는 값.
 *
 * <p>PUT 은 세 가지가 서로 다른 뜻이다(사용자 결정 2026-09-07, QA #96).
 * <table border="1">
 *   <caption>본문 → 뜻</caption>
 *   <tr><th>본문</th><th>뜻</th><th>이 값</th></tr>
 *   <tr><td>키가 없다</td><td>건드리지 마라</td><td>{@link #absent()} — {@code present=false}</td></tr>
 *   <tr><td>{@code "key": null}</td><td>지워라</td><td>{@code present=true, value=null}</td></tr>
 *   <tr><td>{@code "key": 값}</td><td>이 값으로 바꿔라</td><td>{@code present=true, value=값}</td></tr>
 * </table>
 *
 * <p><b>왜 {@code Optional} 을 그대로 안 쓰나.</b> 입구(요청 DTO)에서는 Jackson 이 주는 것이
 * {@code Optional} 이고 <b>참조 자체가 {@code null} 인 것</b>이 "키 없음" 이다
 * ({@code AbsentAwareOptionalModule}). 그 규칙을 서비스·도메인까지 끌고 가면 어디선가
 * {@code opt.isPresent()} 를 부르는 순간 NPE 가 난다. 경계에서 {@link #from(Optional)} 로 한 번
 * 옮겨 담고, 그 뒤로는 {@code null} 참조가 없다.
 *
 * <p>쓰는 자리는 거의 항상 {@link #orKeep(Object)} 한 줄이다 —
 * {@code memo.updateMemo(command.title().orKeep(memo.getTitle()), ...)}.
 */
public record Patch<T>(boolean present, T value) {

    private static final Patch<?> ABSENT = new Patch<>(false, null);

    /** 키가 없었다 — 무엇도 바꾸지 않는다. */
    @SuppressWarnings("unchecked")
    public static <T> Patch<T> absent() {
        return (Patch<T>) ABSENT;
    }

    /** 키가 있었다 — {@code null} 이면 "지운다" 는 뜻이다. */
    public static <T> Patch<T> set(T value) {
        return new Patch<>(true, value);
    }

    /**
     * Jackson 이 채운 {@code Optional} 을 읽는다.
     *
     * <p><b>참조가 {@code null} = 키 없음</b>, {@code Optional.empty()} = 명시적 {@code null}.
     * 이 규칙을 아는 자리는 여기 하나뿐이다.
     */
    public static <T> Patch<T> from(Optional<T> raw) {
        return raw == null ? absent() : new Patch<>(true, raw.orElse(null));
    }

    /** 실렸으면 그 값, 아니면 지금 값. */
    public T orKeep(T current) {
        return present ? value : current;
    }

    /**
     * 실린 값을 다른 타입으로 옮긴다 — 아이디 → 엔티티, 문자열 → 날짜처럼.
     *
     * <p>키가 없으면 변환도 안 한다(조회를 아끼는 게 아니라, 없는 값을 조회하면 404 가 난다).
     * 명시적 {@code null} 은 {@code null} 그대로 통과한다 — "지운다" 를 변환할 것은 없다.
     */
    public <R> Patch<R> map(Function<? super T, ? extends R> mapper) {
        if (!present) {
            return absent();
        }
        return new Patch<>(true, value == null ? null : mapper.apply(value));
    }
}
