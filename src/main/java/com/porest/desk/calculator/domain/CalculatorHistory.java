package com.porest.desk.calculator.domain;

import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "calculator_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalculatorHistory extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "expression", nullable = false, length = 500)
    private String expression;

    @Column(name = "result", nullable = false, length = 100)
    private String result;

    public static CalculatorHistory createHistory(User user, String expression, String result) {
        CalculatorHistory history = new CalculatorHistory();
        history.user = user;
        history.expression = expression;
        history.result = result;
        return history;
    }
}
