package com.porest.desk.common.util;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;

/**
 * 사용자가 붙이는 이름의 저장 전 정규화 — 라벨·태그·폴더·카테고리·프리셋·저축목표·관심목록 그룹·정산 참가자.
 *
 * <p><b>왜 한 곳에 두는가.</b> 관심목록 그룹만 자기 서비스 안에 {@code normalizeGroupName} 을
 * 갖고 있었고 나머지 일곱은 받은 문자열을 그대로 저장했다. 도메인마다 trim 을 따로 베끼면
 * 규칙이 갈라지고, 이름 유일성을 DB UNIQUE 로 받는 순간 그 어긋남이 500 으로 나온다.
 *
 * <h4>trim 하나면 되는 이유 — 대소문자·끝공백은 이미 DB 가 본다</h4>
 * 이름 컬럼의 콜레이션이 {@code utf8mb4_unicode_ci}(PAD SPACE) 라, 중복 검사가 타는
 * QueryDSL {@code eq} → SQL {@code =} 는 <b>이미</b> 대소문자와 끝공백을 무시한다.
 * 즉 {@code "Food"} 와 {@code "food "} 는 서버 코드가 아무것도 안 해도 같은 이름이다.
 * 서비스 검사와 DB UNIQUE 의 판정이 어긋나는 자리는 <b>선행 공백</b> 하나뿐이었다 —
 * {@code " 식비"} 와 {@code "식비"} 는 서비스 검사를 나란히 통과하고 DB 는 서로 다른 값으로 본다.
 * 그래서 여기서 {@link String#trim()} 만 하면 두 판정이 정확히 겹친다.
 *
 * <p><b>{@code toLowerCase} 는 하지 않는다.</b> 저장 값은 사용자가 친 대소문자 그대로 남아야 한다
 * (판정은 콜레이션이 이미 무시한다). 마찬가지로 <b>DB 생성 컬럼에 {@code TRIM()}·{@code LOWER()} 를
 * 넣어서도 안 된다</b> — 그러면 서비스 검사와 UNIQUE 판정이 다시 갈린다. 정규화는 여기 한 곳이다.
 *
 * <h4>상한은 컬럼 폭이다</h4>
 * {@code maxLength} 에는 {@link com.porest.desk.common.validation.FieldLimits#NAME_MAX} 또는
 * {@link com.porest.desk.common.validation.FieldLimits#WIDE_NAME_MAX} 를 넘긴다. 화면(웹·앱)이
 * 12자에서 끊는다고 그 값을 서버에 옮기면, 그보다 긴 이름을 가진 <b>기존 행의 수정 저장이
 * 전부 400</b> 이 된다 — 수정 요청은 이름을 그대로 다시 보내기 때문이다.
 */
public final class NameNormalizer {

    private NameNormalizer() {
    }

    /**
     * 앞뒤 공백을 떼고 돌려준다. 빈 이름이거나 상한을 넘으면 {@code COMMON_400} 으로 거절한다.
     *
     * @param rawName   요청이 보낸 원본(널 허용 — 빈 이름과 같게 본다)
     * @param maxLength 컬럼 폭(FieldLimits 의 NAME_MAX · WIDE_NAME_MAX)
     */
    public static String require(String rawName, int maxLength) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > maxLength) {
            throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
        }
        return name;
    }
}
