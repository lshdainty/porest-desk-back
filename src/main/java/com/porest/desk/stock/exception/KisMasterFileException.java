package com.porest.desk.stock.exception;

/** KIS 마스터파일 다운로드·해제 실패. 시장 단위로 잡아서 다른 시장 동기화를 막지 않는다. */
public class KisMasterFileException extends RuntimeException {

    public KisMasterFileException(String message) {
        super(message);
    }

    public KisMasterFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
