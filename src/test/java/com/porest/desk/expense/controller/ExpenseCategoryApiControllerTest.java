package com.porest.desk.expense.controller;

import com.porest.core.util.MessageResolver;
import com.porest.desk.common.config.web.WebConfig;
import com.porest.desk.expense.service.ExpenseCategoryService;
import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.security.filter.JwtAuthenticationFilter;
import com.porest.desk.security.resolver.LoginUserArgumentResolver;
import com.porest.desk.support.security.WithLoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ExpenseCategory API 슬라이스 테스트.
 *
 * <p>보안 필터는 끄고({@code addFilters=false}) {@link WithLoginUser} 로 SecurityContext 를 세팅 →
 * {@code @LoginUser} ArgumentResolver({@link WebConfig} 가 등록)가 UserPrincipal 을 주입한다.
 * 서비스는 mock — 컨트롤러의 매핑·바디 역직렬화·로그인 사용자 위임을 검증한다.
 */
@WebMvcTest(controllers = ExpenseCategoryApiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, LoginUserArgumentResolver.class})
@ActiveProfiles("test")
@WithLoginUser(rowId = 1L)
class ExpenseCategoryApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExpenseCategoryService expenseCategoryService;
    // porest-core GlobalExceptionHandler(@ControllerAdvice) 의존 — 슬라이스 로드용 mock.
    @MockitoBean private MessageResolver messageResolver;

    private ExpenseCategoryServiceDto.CategoryInfo sampleInfo() {
        return new ExpenseCategoryServiceDto.CategoryInfo(
                100L, 1L, "식비", "utensils", "#fff",
                ExpenseType.EXPENSE, 0, null, false, null, null);
    }

    @Test
    @DisplayName("POST /expense/category — 로그인 사용자·바디로 createCategory 위임")
    void createCategory() throws Exception {
        given(expenseCategoryService.createCategory(any())).willReturn(sampleInfo());

        String body = """
                {"categoryName":"식비","icon":"utensils","color":"#fff","expenseType":"EXPENSE"}
                """;

        mockMvc.perform(post("/api/v1/expense/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(ExpenseCategoryServiceDto.CreateCommand.class);
        verify(expenseCategoryService).createCategory(captor.capture());
        assertThat(captor.getValue().userRowId()).isEqualTo(1L);
        assertThat(captor.getValue().categoryName()).isEqualTo("식비");
        assertThat(captor.getValue().expenseType()).isEqualTo(ExpenseType.EXPENSE);
    }

    @Test
    @DisplayName("GET /expense/categories — 로그인 사용자로 목록 조회")
    void getCategories() throws Exception {
        given(expenseCategoryService.getCategories(1L)).willReturn(List.of(sampleInfo()));

        mockMvc.perform(get("/api/v1/expense/categories"))
                .andExpect(status().isOk());

        verify(expenseCategoryService).getCategories(1L);
    }

    @Test
    @DisplayName("DELETE /expense/category/{id} — id·로그인 사용자로 삭제 위임")
    void deleteCategory() throws Exception {
        mockMvc.perform(delete("/api/v1/expense/category/{id}", 100L))
                .andExpect(status().isOk());

        verify(expenseCategoryService).deleteCategory(eq(100L), eq(1L));
    }
}
