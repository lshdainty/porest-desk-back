package com.porest.desk.stock.client.parser;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 고정폭 레코드의 필드 배치. <b>레이아웃은 코드가 아니라 데이터다.</b>
 *
 * <p>NH 는 마스터파일마다 {@code .h} 구조체를 함께 배포하고, 그 안에 필드 이름·길이·레코드 크기가
 * 전부 적혀 있다. 그래서 오프셋을 손으로 세어 상수로 박을 이유가 없다 —
 * {@code .h} 의 필드 목록을 그대로 옮기면 오프셋은 누계로 나온다.
 *
 * <p>생성 시 <b>필드 길이 합 == 선언된 레코드 크기</b>를 검증한다. {@code .h} 의
 * {@code MST_ASSERT_SIZE} 와 같은 장치다 — 필드를 하나 고치고 레코드 크기를 안 고치면
 * 파일을 읽기 전에 여기서 멈춘다. 안 그러면 이후 전 필드가 조용히 한 칸씩 밀린다.
 *
 * <p>길이는 <b>바이트</b> 단위다. CP949 한글은 2바이트라 문자 단위로 자르면 필드가 통째로 밀린다.
 */
public final class FixedWidthLayout {

    private static final Charset CP949 = Charset.forName("MS949");

    private final int recordSize;
    private final Map<String, int[]> fields = new LinkedHashMap<>();

    /**
     * {@code .h} 의 FIELDS 목록을 (이름, 길이) 순서쌍으로 그대로 옮긴다.
     *
     * @param declaredRecordSize {@code .h} 의 {@code @record} 값
     * @param nameThenLength     이름(String)·길이(Integer)가 번갈아 오는 배열
     */
    public static FixedWidthLayout of(int declaredRecordSize, Object... nameThenLength) {
        return new FixedWidthLayout(declaredRecordSize, nameThenLength);
    }

    private FixedWidthLayout(int declaredRecordSize, Object... nameThenLength) {
        if (nameThenLength.length % 2 != 0) {
            throw new IllegalArgumentException("이름·길이가 짝을 이루지 않는다");
        }
        int offset = 0;
        for (int i = 0; i < nameThenLength.length; i += 2) {
            String name = (String) nameThenLength[i];
            int length = (Integer) nameThenLength[i + 1];
            fields.put(name, new int[]{offset, length});
            offset += length;
        }
        if (offset != declaredRecordSize) {
            throw new IllegalStateException(
                "레코드 크기 불일치 — 선언 %d, 필드 합 %d. 구조 개정을 확인하라".formatted(declaredRecordSize, offset));
        }
        this.recordSize = declaredRecordSize;
    }

    public int recordSize() {
        return recordSize;
    }

    /** 선언 순서대로의 필드 이름. 레이아웃을 훑어야 하는 쪽(픽스처 생성·진단)이 쓴다. */
    public List<String> fieldNames() {
        return List.copyOf(fields.keySet());
    }

    /** 필드 길이(바이트). 레이아웃에 없으면 0. */
    public int lengthOf(String field) {
        int[] pos = fields.get(field);
        return pos == null ? 0 : pos[1];
    }

    /**
     * 레코드에서 필드를 꺼낸다. CP949 로 읽고 <b>우측 공백만</b> 지운다 —
     * 좌측 공백이 의미를 갖는 필드가 있어 {@code strip()} 을 쓰면 값이 손상된다.
     */
    public String read(byte[] record, String field) {
        int[] pos = fields.get(field);
        if (pos == null) {
            throw new IllegalArgumentException("레이아웃에 없는 필드: " + field);
        }
        return stripTrailing(new String(record, pos[0], pos[1], CP949));
    }

    /** 레코드에서 필드를 꺼내되 비어 있으면 null. */
    public String readOrNull(byte[] record, String field) {
        String value = read(record, field);
        return value.isEmpty() ? null : value;
    }

    private static String stripTrailing(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }
}
