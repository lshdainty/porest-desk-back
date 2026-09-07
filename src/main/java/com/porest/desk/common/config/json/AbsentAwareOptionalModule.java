package com.porest.desk.common.config.json;

import org.springframework.stereotype.Component;
import tools.jackson.core.Version;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.ext.jdk8.Jdk8OptionalDeserializer;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.type.ReferenceType;

import java.util.Optional;

/**
 * 요청 본문에서 <b>"키가 없다" 와 {@code "key": null} 을 가른다</b>.
 *
 * <p>PUT 의 뜻이 "없으면 유지, 지우려면 명시적 null" 이므로(사용자 결정 2026-09-07, QA #96)
 * 요청 DTO 가 그 둘을 구분할 수 있어야 한다. Jackson 은 {@code Optional} 로 셋을 표현할 수 있지만
 * <b>기본값 그대로는 레코드에서 못 가른다</b> — 이 레포의 DTO 는 전부 레코드다.
 *
 * <h4>기본 동작(실측, Jackson 3.1.0 + 이 레포의 레코드 DTO)</h4>
 * <pre>
 *   {}                → Optional.empty   ← 키가 없는데도
 *   {"title": null}   → Optional.empty   ← 명시적 null 과 같은 값이 된다
 *   {"title": "hi"}   → Optional[hi]
 * </pre>
 * 레코드는 생성자로 만들어지므로 빠진 칸을 무엇으로 채울지 역직렬화기에 묻는데
 * ({@code getAbsentValue()}), 기본 구현이 {@code getNullValue()} 를 그대로 돌려준다.
 * 그래서 둘이 한 값으로 뭉친다.
 *
 * <h4>이 모듈을 끼우면</h4>
 * <pre>
 *   {}                → null 참조        (키 없음 — 건드리지 마라)
 *   {"title": null}   → Optional.empty   (지워라)
 *   {"title": "hi"}   → Optional[hi]     (바꿔라)
 * </pre>
 * {@code getAbsentValue()} 만 {@code null} 로 바꾼다. 값 해석·타입 변환·{@code TypeDeserializer} 는
 * 전부 Jackson 기본 구현({@link Jdk8OptionalDeserializer})이 하던 그대로다 — 문자열·숫자·날짜·
 * enum·리스트가 다 같이 따라온다.
 *
 * <p>{@code null} 참조를 서비스까지 들고 다니지는 않는다. 컨트롤러가
 * {@code com.porest.desk.common.patch.Patch#from(Optional)} 으로 한 번 옮겨 담는다.
 *
 * <h4>왜 {@code JsonNullable} 도 {@code Map} 도 아닌가</h4>
 * <ul>
 *   <li>{@code org.openapitools:jackson-databind-nullable} 의 {@code JsonNullable} 은
 *       <b>Jackson 2 용</b>이다({@code com.fasterxml.jackson.databind.Module}). 부트 4 의 MVC 는
 *       Jackson 3({@code tools.jackson.databind})으로 읽으므로 그 모듈은 등록조차 되지 않는다.
 *       의존성만 늘고 동작은 안 한다.</li>
 *   <li>{@code Map<String,Object>} 본문은 키 존재를 알 수 있지만 {@code @Valid} 제약·타입 변환·
 *       OpenAPI 스키마를 통째로 잃는다 — 방금 채운 입력 검증(QA #85·#86)이 그대로 사라진다.</li>
 * </ul>
 *
 * <p>{@code Optional} 은 그대로 두면 {@code @Size}·{@code @NotBlank} 도 계속 걸린다
 * (Hibernate Validator 의 {@code Optional} 값 추출기). 즉 <b>키가 없으면 검증도 건너뛰고</b>,
 * 명시적 {@code null} 은 필수 칸에서 400 이 된다 — 안 보낸 것이 필수 검증을 우회하지 않는다.
 */
@Component
public class AbsentAwareOptionalModule extends JacksonModule {

    @Override
    public String getModuleName() {
        return "porest-absent-aware-optional";
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addDeserializers(new OptionalDeserializers());
    }

    private static final class OptionalDeserializers implements Deserializers {

        @Override
        public boolean hasDeserializerFor(DeserializationConfig config, Class<?> valueType) {
            return Optional.class.isAssignableFrom(valueType);
        }

        @Override
        public ValueDeserializer<?> findReferenceDeserializer(ReferenceType refType,
                DeserializationConfig config, BeanDescription.Supplier beanDescRef,
                TypeDeserializer contentTypeDeserializer, ValueDeserializer<?> contentDeserializer) {
            if (!refType.isTypeOrSubTypeOf(Optional.class)) {
                return null;
            }
            return new AbsentAwareOptionalDeserializer(refType, null,
                    contentTypeDeserializer, contentDeserializer);
        }
    }

    private static final class AbsentAwareOptionalDeserializer extends Jdk8OptionalDeserializer {

        AbsentAwareOptionalDeserializer(JavaType fullType, ValueInstantiator valueInstantiator,
                TypeDeserializer typeDeserializer, ValueDeserializer<?> valueDeserializer) {
            super(fullType, valueInstantiator, typeDeserializer, valueDeserializer);
        }

        @Override
        public Jdk8OptionalDeserializer withResolved(TypeDeserializer typeDeserializer,
                ValueDeserializer<?> valueDeserializer) {
            return new AbsentAwareOptionalDeserializer(_fullType, _valueInstantiator,
                    typeDeserializer, valueDeserializer);
        }

        /** 키가 없을 때 채울 값 — 기본 구현은 {@code Optional.empty()}, 여기서는 "없음" 이다. */
        @Override
        public Object getAbsentValue(DeserializationContext ctxt) {
            return null;
        }
    }
}
