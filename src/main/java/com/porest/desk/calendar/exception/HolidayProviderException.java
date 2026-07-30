package com.porest.desk.calendar.exception;

/**
 * 외부 공휴일 소스 호출·파싱 실패.
 *
 * <p>동기화는 스케줄러·기동 러너에서만 돌고 HTTP 응답으로 새어 나가지 않으므로 도메인 에러코드를 붙이지 않는다.
 * 이 예외는 동기화 서비스가 폴백 소스로 넘어가는 신호로만 쓴다.
 */
public class HolidayProviderException extends RuntimeException {

    public HolidayProviderException(String message) {
        super(message);
    }

    public HolidayProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
