package com.stock.spai.service;

import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.StockAnalysisContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 專責組裝 LINE Flex Message payload，避免訊息送出邏輯與畫面資料組裝耦合。
 */
@Component
public class LineFlexMessageBuilder {

    private static final int ALT_TEXT_MAX_LENGTH = 120;
    private static final int SUMMARY_MAX_LENGTH = 160;
    private static final List<String> GENERIC_SUMMARY_TITLES = List.of(
            "分析摘要",
            "ai分析摘要",
            "結論摘要",
            "投資建議",
            "操作建議",
            "綜合分析",
            "總結",
            "結論"
    );

    public Map<String, Object> buildProfessionalFlexPayload(String userId, AiAnalysisResult analysisResult) {
        String symbol = resolveSymbol(analysisResult);
        String name = resolveName(analysisResult);
        String summary = resolveDisplaySummary(analysisResult);

        return Map.of(
                "to", safeText(userId, ""),
                "messages", List.of(
                        Map.of(
                                "type", "flex",
                                "altText", buildAltText(symbol, name, summary),
                                "contents", buildBubble(analysisResult, symbol, name, summary)
                        )
                )
        );
    }

    private Map<String, Object> buildBubble(AiAnalysisResult analysisResult,
                                            String symbol,
                                            String name,
                                            String summary) {
        StockAnalysisContext context = analysisResult == null ? null : analysisResult.getContext();
        String latestDate = context == null ? "" : safeText(context.getLatestDataDate(), "");

        return Map.of(
                "type", "bubble",
                "header", Map.of(
                        "type", "box",
                        "layout", "vertical",
                        "backgroundColor", "#0B5CAD",
                        "paddingAll", "16px",
                        "contents", List.of(
                                Map.of(
                                        "type", "text",
                                        "text", symbol + " " + name,
                                        "weight", "bold",
                                        "size", "lg",
                                        "color", "#FFFFFF",
                                        "wrap", true
                                ),
                                Map.of(
                                        "type", "text",
                                        "text", latestDate.isBlank() ? "每日分析通知" : "資料日期：" + latestDate,
                                        "size", "xs",
                                        "color", "#D9E8FF",
                                        "margin", "sm"
                                )
                        )
                ),
                "body", Map.of(
                        "type", "box",
                        "layout", "vertical",
                        "spacing", "md",
                        "contents", buildBodyContents(analysisResult, summary)
                )
        );
    }

    private List<Object> buildBodyContents(AiAnalysisResult analysisResult, String summary) {
        StockAnalysisContext context = analysisResult == null ? null : analysisResult.getContext();
        String latestPriceSummary = context == null
                ? "暫無最新價格摘要"
                : safeText(context.getLatestPriceSummary(), "暫無最新價格摘要");
        String volumeSummary = context == null
                ? "暫無量能摘要"
                : safeText(context.getVolumeSummary(), "暫無量能摘要");

        String stance = analysisResult == null ? "" : safeText(analysisResult.getStance(), "");

        if (stance.isBlank()) {
            return List.of(
                    createDataRow("價格摘要", latestPriceSummary),
                    createDataRow("量能摘要", volumeSummary),
                    Map.of("type", "separator", "margin", "sm"),
                    createSummaryBox(summary)
            );
        }

        return List.of(
                createDataRow("價格摘要", latestPriceSummary),
                createDataRow("量能摘要", volumeSummary),
                createDataRow("Stance", stance),
                Map.of("type", "separator", "margin", "sm"),
                createSummaryBox(summary)
        );
    }

    private Map<String, Object> createDataRow(String label, String value) {
        return Map.of(
                "type", "box",
                "layout", "baseline",
                "spacing", "sm",
                "contents", List.of(
                        Map.of(
                                "type", "text",
                                "text", label,
                                "size", "sm",
                                "color", "#6B7280",
                                "flex", 3,
                                "wrap", true
                        ),
                        Map.of(
                                "type", "text",
                                "text", value,
                                "size", "sm",
                                "color", "#111827",
                                "weight", "bold",
                                "flex", 7,
                                "align", "end",
                                "wrap", true
                        )
                )
        );
    }

    private Map<String, Object> createSummaryBox(String summary) {
        return Map.of(
                "type", "box",
                "layout", "vertical",
                "backgroundColor", "#F5F7FA",
                "cornerRadius", "12px",
                "paddingAll", "12px",
                "contents", List.of(
                        Map.of(
                                "type", "text",
                                "text", "AI 結論摘要",
                                "size", "sm",
                                "weight", "bold",
                                "color", "#0B5CAD"
                        ),
                        Map.of(
                                "type", "text",
                                "text", summary,
                                "size", "sm",
                                "color", "#1F2937",
                                "wrap", true,
                                "margin", "sm"
                        )
                )
        );
    }

    String resolveDisplaySummary(AiAnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "AI 摘要資訊有限，請搭配完整分析內容判讀。";
        }

        String summary = normalize(analysisResult.getSummary());
        if (isInformativeSummary(summary)) {
            return trimToMaxLength(summary, SUMMARY_MAX_LENGTH);
        }

        String fallbackFromRawText = extractInformativeLine(analysisResult.getRawText(), summary);
        if (!fallbackFromRawText.isBlank()) {
            return trimToMaxLength(fallbackFromRawText, SUMMARY_MAX_LENGTH);
        }

        return "AI 摘要資訊有限，請搭配完整分析內容判讀。";
    }

    private String extractInformativeLine(String rawText, String existingSummary) {
        String normalizedSummary = normalize(existingSummary);

        return normalize(rawText).lines()
                .map(String::trim)
                .map(this::removeMarkdownPrefix)
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !Objects.equals(line, normalizedSummary))
                .filter(this::isInformativeSummary)
                .findFirst()
                .orElse("");
    }

    private boolean isInformativeSummary(String summary) {
        String normalized = normalize(summary);
        if (normalized.isBlank()) {
            return false;
        }

        String lowercase = normalized.toLowerCase();
        if (GENERIC_SUMMARY_TITLES.contains(lowercase)) {
            return false;
        }

        if (normalized.length() <= 4) {
            return false;
        }

        if (normalized.length() <= 10 && !containsInsightKeyword(normalized)) {
            return false;
        }

        return !normalized.matches("^[\\p{IsHan}A-Za-z0-9\\s]+摘要$");
    }

    private boolean containsInsightKeyword(String text) {
        return text.contains("偏多")
                || text.contains("偏空")
                || text.contains("觀望")
                || text.contains("留意")
                || text.contains("支撐")
                || text.contains("壓力")
                || text.contains("風險")
                || text.contains("趨勢")
                || text.contains("量能")
                || text.contains("法人")
                || text.contains("融資")
                || text.contains("買")
                || text.contains("賣");
    }

    private String buildAltText(String symbol, String name, String summary) {
        return trimToMaxLength(symbol + " " + name + "：" + summary, ALT_TEXT_MAX_LENGTH);
    }

    private String resolveSymbol(AiAnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "未知代號";
        }

        String symbol = normalize(analysisResult.getSymbol());
        if (!symbol.isBlank()) {
            return symbol;
        }

        StockAnalysisContext context = analysisResult.getContext();
        return context == null ? "未知代號" : safeText(context.getSymbol(), "未知代號");
    }

    private String resolveName(AiAnalysisResult analysisResult) {
        if (analysisResult == null) {
            return "未命名股票";
        }

        String name = normalize(analysisResult.getName());
        if (!name.isBlank()) {
            return name;
        }

        StockAnalysisContext context = analysisResult.getContext();
        return context == null ? "未命名股票" : safeText(context.getName(), "未命名股票");
    }

    private String removeMarkdownPrefix(String text) {
        return normalize(text)
                .replaceFirst("^#+\\s*", "")
                .replaceFirst("^[\\-*]\\s*", "");
    }

    private String trimToMaxLength(String text, int maxLength) {
        String normalized = normalize(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private String safeText(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized.isBlank() ? defaultValue : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
