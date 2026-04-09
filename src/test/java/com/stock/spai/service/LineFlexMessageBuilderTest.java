package com.stock.spai.service;

import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.StockAnalysisContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class LineFlexMessageBuilderTest {

    private final LineFlexMessageBuilder builder = new LineFlexMessageBuilder();

    @Test
    void buildProfessionalFlexPayload_shouldPreferSectionBasedSummaryAndCleanMarkdown() {
        StockAnalysisContext context = new StockAnalysisContext();
        context.setSymbol("2330");
        context.setName("台積電");
        context.setIndustryCategory("半導體業");
        context.setLatestDataDate("2026-04-07");
        context.setLatestClosePrice("912.00");
        context.setPriceChange("+12.00");
        context.setPriceChangePercent("+1.33%");
        context.setLatestVolume("32,456,789");
        context.setForeignInvestorSummary("買賣超 +3,200");
        context.setMarginSummary("買進 1,250，餘額 18,600，較前日 +450");

        AiAnalysisResult analysisResult = new AiAnalysisResult(
                "2330",
                "台積電",
                """
                ## 綜合建議
                **結論：偏多**
                好的，以下為本次分析摘要。
                量能回溫且收盤站穩短期均線，短線仍由多方主導，但不宜追高。

                ## 技術面總結
                * 價格維持在短中期均線之上，量能回溫，技術面仍偏多。

                ## 風險提示
                - 若外資買盤無法延續，或量能再度收斂，漲勢可能轉為高檔震盪。
                """,
                "AI 分析摘要",
                "偏多",
                context
        );

        Map<String, Object> payload = builder.buildProfessionalFlexPayload("U123456", analysisResult);

        assertEquals("U123456", payload.get("to"));

        Map<String, Object> message = getFirstMessage(payload);
        assertEquals("flex", message.get("type"));
        assertTrue(((String) message.get("altText")).contains("結論偏多"));
        assertFalse(((String) message.get("altText")).contains("好的，以下為本次分析摘要"));
        assertFalse(((String) message.get("altText")).contains("**"));

        Map<String, Object> contents = getMap(message, "contents");
        Map<String, Object> header = getMap(contents, "header");
        Map<String, Object> body = getMap(contents, "body");
        List<?> bodyContents = getList(body, "contents");
        List<?> headerContents = getList(header, "contents");

        assertEquals("產業別：半導體業", castMap(headerContents.get(1)).get("text"));
        assertEquals("912.00", findRowValue(bodyContents, "收盤價"));
        assertEquals("+12.00", findRowValue(bodyContents, "漲跌"));
        assertEquals("+1.33%", findRowValue(bodyContents, "漲跌幅"));
        assertEquals("32,456,789", findRowValue(bodyContents, "成交量"));
        assertEquals("買賣超 +3,200", findRowValue(bodyContents, "外資摘要"));
        assertEquals("買進 1,250，餘額 18,600，較前日 +450", findRowValue(bodyContents, "融資摘要"));
        assertEquals("偏多", findRowValue(bodyContents, "趨勢判斷"));

        Map<String, Object> summaryBox = castMap(bodyContents.get(bodyContents.size() - 1));
        List<?> summaryContents = getList(summaryBox, "contents");
        assertEquals("AI 結論摘要", castMap(summaryContents.get(0)).get("text"));
        assertEquals(
                "結論偏多，量能回溫且收盤站穩短期均線，短線仍由多方主導，但不宜追高。\n理由是，價格維持在短中期均線之上，量能回溫，技術面仍偏多。\n風險是，若外資買盤無法延續，或量能再度收斂，漲勢可能轉為高檔震盪。",
                castMap(summaryContents.get(1)).get("text")
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFirstMessage(Map<String, Object> payload) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        return messages.getFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private List<?> getList(Map<String, Object> source, String key) {
        return (List<?>) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private String findRowValue(List<?> bodyContents, String label) {
        return bodyContents.stream()
                .filter(Map.class::isInstance)
                .map(this::castMap)
                .filter(item -> "box".equals(item.get("type")))
                .filter(item -> "baseline".equals(item.get("layout")))
                .map(item -> getList(item, "contents"))
                .filter(contents -> !contents.isEmpty())
                .filter(contents -> label.equals(castMap(contents.get(0)).get("text")))
                .map(contents -> (String) castMap(contents.get(1)).get("text"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到欄位: " + label));
    }
}
