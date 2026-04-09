package com.stock.spai.service;

import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.StockAnalysisContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("unit")
class AiAnalysisResultBuilderTest {

    private final AiAnalysisResultBuilder aiAnalysisResultBuilder = new AiAnalysisResultBuilder();

    @Test
    void build_shouldBuildLayeredSummaryFromFixedSectionsAndKeepStanceAligned() {
        StockAnalysisContext context = new StockAnalysisContext();
        context.setSymbol("2330");
        context.setName("台積電");

        AiAnalysisResult result = aiAnalysisResultBuilder.build(
                "2330",
                "台積電",
                """
                ## 今日重點
                - 短線出現反彈，但仍未改變整體整理格局。

                ## 綜合建議
                **結論：偏空**
                好的，以下為本次分析摘要。
                反彈暫時視為跌深後修正，上方仍有明顯賣壓，短線操作宜保守。

                ## 技術面總結
                * 雖有短線反彈，但股價仍在季線下方，量能未明顯放大，技術面偏弱。
                * 近期若無法站回前波壓力區，趨勢仍以弱勢整理看待。

                ## 風險提示
                - 若外資賣壓擴大或失守前低，波動可能進一步放大。
                """,
                context
        );

        assertEquals("2330", result.getSymbol());
        assertEquals("台積電", result.getName());
        assertEquals(
                "結論偏空，反彈暫時視為跌深後修正，上方仍有明顯賣壓，短線操作宜保守。 理由是，雖有短線反彈，但股價仍在季線下方，量能未明顯放大，技術面偏弱。 風險是，若外資賣壓擴大或失守前低，波動可能進一步放大。",
                result.getSummary()
        );
        assertFalse(result.getSummary().contains("**"));
        assertFalse(result.getSummary().contains("好的"));
        assertEquals("偏空", result.getStance());
        assertEquals(context, result.getContext());
    }

    @Test
    void buildSummary_shouldPreferReasonFromChipSectionAndKeepRiskSentence() {
        String summary = aiAnalysisResultBuilder.buildSummary(
                """
                ## 綜合建議
                **結論：偏多**
                短線仍可偏多看待，但不宜追高。

                ## 籌碼／資金面觀察
                - 近三日外資買超 3,200 張、融資增加 450 張，成交量來到 32,456,789 股。
                - 外資續站買方，融資未明顯失控，籌碼面仍有支撐。

                ## 風險提示
                - 若量能無法延續，或跌破短線支撐，走勢可能轉為震盪。
                """
        );

        assertEquals(
                "結論偏多，短線仍可偏多看待，但不宜追高。 理由是，外資續站買方，融資未明顯失控，籌碼面仍有支撐。 風險是，若量能無法延續，或跌破短線支撐，走勢可能轉為震盪。",
                summary
        );
        assertFalse(summary.contains("32,456,789"));
    }
}
