package com.porest.desk.support.message;

import com.porest.desk.notification.service.NotificationMessages;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 단위 테스트에서 <b>실물 메시지 번들</b>을 읽는 {@link NotificationMessages} 를 만든다.
 *
 * <p>알림 문구는 DB 에 굳는다 — 그래서 "무슨 문장이 저장되는가" 가 회귀 대상인데, 여기서
 * {@code MessageSource} 를 mock 으로 두면 그 문장을 아무것도 안 지키게 된다. basename 순서는
 * {@code application.yml} 과 같게 둔다(desk 가 core 를 이긴다).
 */
public final class TestMessages {

    private TestMessages() {}

    public static NotificationMessages notificationMessages() {
        return new NotificationMessages(messageSource());
    }

    public static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("message/messages", "message/core-messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}
