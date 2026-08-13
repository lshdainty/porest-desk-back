package com.porest.desk.dataimport.sms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SmsMerchantHints — 가맹점 키워드 사전")
class SmsMerchantHintsTest {

    @Test
    @DisplayName("지점명이 붙어 있어도 브랜드로 찾는다")
    void withBranchSuffix() {
        assertThat(SmsMerchantHints.categoryNamesFor("스타벅스강남R점")).contains("카페");
        assertThat(SmsMerchantHints.categoryNamesFor("GS25 역삼점")).contains("편의점");
    }

    @Test
    @DisplayName("앞부분을 공유하는 브랜드는 긴 토큰이 이긴다 — GS25 가 GS칼텍스로 새지 않는다")
    void longestTokenWins() {
        assertThat(SmsMerchantHints.categoryNamesFor("GS25강남점")).contains("편의점");
        assertThat(SmsMerchantHints.categoryNamesFor("GS칼텍스 방배주유소")).contains("주유");
    }

    @Test
    @DisplayName("모르는 가맹점은 빈 목록 — 억지로 추측하지 않는다")
    void unknownMerchant() {
        assertThat(SmsMerchantHints.categoryNamesFor("동네철물점")).isEmpty();
        assertThat(SmsMerchantHints.categoryNamesFor(null)).isEmpty();
        assertThat(SmsMerchantHints.categoryNamesFor("  ")).isEmpty();
    }

    @Test
    @DisplayName("영문 상호도 대소문자 무관하게 찾는다")
    void caseInsensitive() {
        assertThat(SmsMerchantHints.categoryNamesFor("netflix.com")).contains("문화생활");
        assertThat(SmsMerchantHints.categoryNamesFor("COSTCO WHOLESALE")).contains("마트");
    }
}
