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
    void build_shouldSkipGenericOpeningAndKeepInformativeSummary_whenRawTextContainsBullishSignal() {
        StockAnalysisContext context = new StockAnalysisContext();
        context.setSymbol("2330");
        context.setName("台積電");

        AiAnalysisResult result = aiAnalysisResultBuilder.build(
                "2330",
                "台積電",
                """
                ## 今日重點
                今日重點：量能回溫且收盤站穩月線，短線偏多，但仍要留意高檔震盪風險。

                ## 綜合建議
                結論：偏多
                """,
                context
        );

        assertEquals("2330", result.getSymbol());
        assertEquals("台積電", result.getName());
        assertEquals("量能回溫且收盤站穩月線，短線偏多，但仍要留意高檔震盪風險。", result.getSummary());
        assertEquals("偏多", result.getStance());
        assertEquals(context, result.getContext());
    }
}
