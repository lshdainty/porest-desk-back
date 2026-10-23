package io.groo.damda.securities.service.dto;

/**
 * 투자자별 매매동향 하루 — 토스와 나무가 <b>같은 모양</b>으로 내린다. 순매수 <b>주</b> 수, 문자열,
 * 모든 칸 null 허용.
 *
 * <p>토스는 당일 잠정 기록의 개인 칸이 null 로 온다 — 그대로 null 이다.
 *
 * @param date        {@code yyyy-MM-dd}
 * @param individual  개인 순매수
 * @param foreigner   외국인 순매수
 * @param institution 기관 순매수
 */
public record InvestorTradingDay(String date, String individual, String foreigner, String institution) {
}
