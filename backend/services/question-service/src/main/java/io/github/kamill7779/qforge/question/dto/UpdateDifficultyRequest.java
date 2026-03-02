package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class UpdateDifficultyRequest {

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("1.00")
    private BigDecimal difficulty;

    public BigDecimal getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(BigDecimal difficulty) {
        this.difficulty = difficulty;
    }
}
