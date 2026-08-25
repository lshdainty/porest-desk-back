package com.porest.desk.namu.client.dto;

import java.util.Set;

/**
 * 나무증권 응답 봉투의 공통 얼굴.
 *
 * <p><b>토스와 결정적으로 다른 점</b> — 나무는 <b>HTTP 200 으로도 실패를 돌려준다.</b>
 * 성공 여부는 {@code rsp_cd} 로만 알 수 있다. 상태코드만 보고 성공으로 넘기면 실패 응답이
 * "조회 결과 없음" 으로 둔갑해 화면이 조용히 빈다.
 *
 * <p>봉투가 셋인 이유는 페이로드 개수다 — 조회에 따라 {@code Output_0} 하나만 오기도 하고
 * (시세), 요약({@code Output_0}) + 목록({@code Output_1})으로 나뉘어 오기도 한다(잔고).
 *
 * <h2>성공 코드가 {@code 00000} 하나가 아니다</h2>
 *
 * <p>NH 는 <b>정상코드 전체 목록을 공식 문서로 공개하지 않는다</b>({@code llms-full.txt} ·
 * {@code common/overview.md} · {@code common/openapi.json} 어디에도 아래 코드가 없다).
 * 근거는 NH 가 배포하는 공식 SDK 소스 {@code nhplug/client.py} 와 공식 참고구현
 * {@code snippets/auth/token_cache/nh_token.py} 다 — 둘 다 아래 네 코드를 성공으로 두고,
 * 목록에 없는 미지의 정상코드를 실패로 오판하지 않으려고 {@code rsp_msg} 의 "완료" 를
 * <b>2차 방어</b>로 함께 본다.
 *
 * <ul>
 *   <li>{@code 00000} — 현재가·계좌목록</li>
 *   <li>{@code 00166} — <b>잔고·자산현황·손익.</b> 우리 자산 화면이 정확히 이 계열이다</li>
 *   <li>{@code 00221} — 매수가능수량(우리는 주문을 안 해 현재 미사용)</li>
 *   <li>{@code 13578} — <b>조회 내역 없음.</b> 실패가 아니라 "정상 조회, 결과 0건" 이다.
 *       이걸 실패로 보면 보유 종목이 없는 정상 상태가 에러 화면으로 뜬다</li>
 * </ul>
 *
 * <p><b>조합은 OR 다(AND 아님).</b> 참고구현의 실패 조건이
 * {@code rsp_cd not in SUCCESS_CODES and "완료" not in rsp_msg} 이므로, 뒤집으면
 * "코드가 목록에 있거나 <b>또는</b> 메시지에 완료가 있으면 성공" 이다.
 *
 * <p><b>{@code 00165}·{@code 00218} 과 헷갈리면 안 된다.</b> 그 둘은 연속조회({@code cts})
 * 계속 신호이지 성공 코드가 아니다 — 숫자만 가깝다. 우리는 연속조회를 쓰지 않는다.
 *
 * <p>넓혀도 안전한 이유: 페이로드가 비어 오는 경우를 호출부가 이미 감당한다.
 * {@code Output_0} 이 null 이면 시세는 null 을, 잔고 요약은 {@code "0"} 을, 목록은 빈
 * 리스트를 돌려준다. 그리고 <b>이 판정은 토큰을 건드리지 않는다</b> — 실패로 떨어져도
 * 재발급 경로로 이어지지 않으므로 알림톡과는 무관하다.
 */
public interface NamuResponse {

    /** 하위 호환용 별칭. 새 코드는 {@link #isSuccess()} 를 쓴다. */
    String SUCCESS = "00000";

    /**
     * 업무 성공으로 확인된 {@code rsp_cd}. 공식 SDK {@code nhplug/client.py} 의
     * {@code DEFAULT_SUCCESS_CODES} 와 같다.
     */
    Set<String> SUCCESS_CODES = Set.of(SUCCESS, "00166", "00221", "13578");

    /** 코드 목록에 없는 미지의 정상코드를 건지는 2차 방어. */
    String SUCCESS_MESSAGE = "완료";

    String rspCd();

    /** 응답메시지. 로그에만 쓴다 — 업스트림 문구를 사용자에게 릴레이하지 않는다. */
    String rspMsg();

    /**
     * {@code rsp_cd} 가 성공 목록에 있거나 <b>또는</b> {@code rsp_msg} 가 완료를 말하면 성공.
     *
     * <p>{@code rsp_cd} 가 아예 없는(null) 응답은 실패로 본다. 공식 SDK 는 "판정 대상 아님"
     * 으로 성공 처리하지만, 우리 봉투에서 null 은 나무 응답이 아닌 무언가를 읽었다는 뜻에
     * 가깝다 — 성공으로 넘기면 그게 빈 화면이 된다.
     */
    default boolean isSuccess() {
        String code = rspCd();
        if (code != null && SUCCESS_CODES.contains(code.trim())) {
            return true;
        }
        String message = rspMsg();
        return code != null && message != null && message.contains(SUCCESS_MESSAGE);
    }
}
