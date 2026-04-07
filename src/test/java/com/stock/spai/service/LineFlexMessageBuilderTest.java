package com.stock.spai.service;

import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.StockAnalysisContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class LineFlexMessageBuilderTest {

    private final LineFlexMessageBuilder builder = new LineFlexMessageBuilder();

    @Test
    void buildProfessionalFlexPayload_shouldUseRawTextFallback_whenSummaryLooksLikeTitleOnly() {
        StockAnalysisContext context = new StockAnalysisContext();
        context.setSymbol("2330");
        context.setName("台積電");
        context.setLatestDataDate("2026-04-07");
        context.setLatestPriceSummary("2026-04-07 收盤 912.00");
        context.setVolumeSummary("2026-04-07 成交量 32,456,789");

        AiAnalysisResult analysisResult = new AiAnalysisResult(
                "2330",
                "台積電",
                """
                ## 分析摘要
                短線量能回溫且收盤站回月線之上，整體偏多，但仍需留意追價風險。
                """,
                "分析摘要",
                "偏多",
                context
        );

        Map<String, Object> payload = builder.buildProfessionalFlexPayload("U123456", analysisResult);

        assertEquals("U123456", payload.get("to"));

        Map<String, Object> message = getFirstMessage(payload);
        assertEquals("flex", message.get("type"));
        assertTrue(((String) message.get("altText")).contains("短線量能回溫且收盤站回月線之上"));

        Map<String, Object> contents = getMap(message, "contents");
        Map<String, Object> body = getMap(contents, "body");
        List<?> bodyContents = getList(body, "contents");

        Map<String, Object> stanceRow = castMap(bodyContents.get(2));
        List<?> stanceContents = getList(stanceRow, "contents");
        assertEquals("Stance", castMap(stanceContents.get(0)).get("text"));
        assertEquals("偏多", castMap(stanceContents.get(1)).get("text"));

        Map<String, Object> summaryBox = castMap(bodyContents.get(4));
        List<?> summaryContents = getList(summaryBox, "contents");
        assertEquals("AI 結論摘要", castMap(summaryContents.get(0)).get("text"));
        assertEquals("短線量能回溫且收盤站回月線之上，整體偏多，但仍需留意追價風險。", castMap(summaryContents.get(1)).get("text"));
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
}
