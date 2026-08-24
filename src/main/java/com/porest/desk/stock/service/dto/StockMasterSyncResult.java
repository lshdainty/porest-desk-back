package com.porest.desk.stock.service.dto;

import com.porest.desk.stock.type.MasterFile;

/**
 * 마스터파일 1개의 동기화 결과.
 *
 * @param file        동기화 대상 파일
 * @param failed      다운로드·파싱·DB 오류로 해당 파일을 통째로 건너뛰었는지
 * @param inserted    새로 적재한 건수
 * @param updated     파일 값이 달라져 갱신한 건수 (재활성·타 소스 보강 포함)
 * @param deactivated 파일에서 사라져 비활성 처리한 건수
 * @param unchanged   변경 없이 넘어간 건수
 */
public record StockMasterSyncResult(
    MasterFile file,
    boolean failed,
    int inserted,
    int updated,
    int deactivated,
    int unchanged
) {
    public static StockMasterSyncResult failed(MasterFile file) {
        return new StockMasterSyncResult(file, true, 0, 0, 0, 0);
    }

    /** 실제로 DB 를 건드렸는지. 변경이 있을 때만 로그를 남겨 매일 도는 동기화의 소음을 줄인다. */
    public boolean hasChanges() {
        return inserted > 0 || updated > 0 || deactivated > 0;
    }
}
