package com.stock.spai.service;

import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.StockAnalysisContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class AiAnalysisResultBuilderTest {

    private final AiAnalysisResultBuilder aiAnalysisResultBuilder = new AiAnalysisResultBuilder();

    @Test
    void build_shouldFillSummaryAndStance_whenRawTextContainsBullishSignal() {
        StockAnalysisContext context = new StockAnalysisContext();
        context.setSymbol("2330");
        context.setName("台積電");

        AiAnalysisResult result = aiAnalysisResultBuilder.build(
                "2330",
                "台積電",
                """
                ## 綜合判斷
                偏多看待，短線可分批佈局，但仍需留意風險。
                """,
                context
        );

        assertEquals("2330", result.getSymbol());
        assertEquals("台積電", result.getName());
        assertEquals("綜合判斷", result.getSummary());
        assertEquals("偏多", result.getStance());
        assertEquals(context, result.getContext());
    }
}
