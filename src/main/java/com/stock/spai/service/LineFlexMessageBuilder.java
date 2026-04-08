package com.stock.spai.service;

import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.StockAnalysisContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 組裝 LINE Flex Message payload，提供第二版較高資訊密度卡片。
 */
@Component
public class LineFlexMessageBuilder {

    private static final int ALT_TEXT_MAX_LENGTH = 120;
    private static final int SUMMARY_MAX_LENGTH = 160;
    private static final String POSITIVE_COLOR = "#D9485F";
    private static final String NEGATIVE_COLOR = "#1C8C5E";
    private static final String NEUTRAL_COLOR = "#111827";
    private static final String MUTED_COLOR = "#6B7280";
    private static final String DEFAULT_SUMMARY = "AI 摘要尚無足夠資訊";

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
                                        "text", latestDate.isBlank() ? "資料日期待補" : "資料日期：" + latestDate,
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
        String priceChange = resolvePriceChange(context);
        String priceChangePercent = resolvePriceChangePercent(context);
        String foreignInvestorSummary = resolveForeignInvestorSummary(context);
        String marginSummary = resolveMarginSummary(context);
        String displayStance = resolveDisplayStance(analysisResult);

        List<Object> contents = new ArrayList<>();
        contents.add(createSectionTitle("今日重點"));
        contents.add(createDataRow("收盤價", resolveClosePrice(context), NEUTRAL_COLOR));
        contents.add(createDataRow("漲跌", priceChange, resolveSignedColor(priceChange)));
        contents.add(createDataRow("漲跌幅", priceChangePercent, resolveSignedColor(priceChangePercent)));
        contents.add(createDataRow("成交量", resolveLatestVolume(context), NEUTRAL_COLOR));
        contents.add(Map.of("type", "separator", "margin", "sm"));
        contents.add(createSectionTitle("籌碼觀察"));
        contents.add(createDataRow("外資摘要", foreignInvestorSummary, resolveSignedColor(foreignInvestorSummary)));
        contents.add(createDataRow("融資摘要", marginSummary, resolveSignedColor(marginSummary)));
        contents.add(createDataRow("趨勢判斷", displayStance, resolveStanceColor(displayStance)));
        contents.add(Map.of("type", "separator", "margin", "sm"));
        contents.add(createSummaryBox(summary));
        return contents;
    }

    private Map<String, Object> createSectionTitle(String title) {
        return Map.of(
                "type", "text",
                "text", title,
                "size", "sm",
                "weight", "bold",
                "color", "#0B5CAD"
        );
    }

    private Map<String, Object> createDataRow(String label, String value, String valueColor) {
        return Map.of(
                "type", "box",
                "layout", "baseline",
                "spacing", "sm",
                "contents", List.of(
                        Map.of(
                                "type", "text",
                                "text", label,
                                "size", "sm",
                                "color", MUTED_COLOR,
                                "flex", 3,
                                "wrap", true
                        ),
                        Map.of(
                                "type", "text",
                                "text", value,
                                "size", "sm",
                                "color", valueColor,
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
            return DEFAULT_SUMMARY;
        }

        String summary = normalize(analysisResult.getSummary());
        if (AiSummaryTextHelper.isInformativeSummary(summary)) {
            return trimToMaxLength(summary, SUMMARY_MAX_LENGTH);
        }

        String fallbackFromRawText = AiSummaryTextHelper.extractBestSummary(analysisResult.getRawText());
        if (!fallbackFromRawText.isBlank()) {
            return trimToMaxLength(fallbackFromRawText, SUMMARY_MAX_LENGTH);
        }

        return DEFAULT_SUMMARY;
    }

    private String buildAltText(String symbol, String name, String summary) {
        return trimToMaxLength(symbol + " " + name + "｜" + summary, ALT_TEXT_MAX_LENGTH);
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

    private String resolveClosePrice(StockAnalysisContext context) {
        if (context == null) {
            return "暫無資料";
        }

        String closePrice = normalize(context.getLatestClosePrice());
        if (!closePrice.isBlank()) {
            return closePrice;
        }
        return safeText(context.getLatestPriceSummary(), "暫無資料");
    }

    private String resolvePriceChange(StockAnalysisContext context) {
        return context == null ? "暫無資料" : safeText(context.getPriceChange(), "暫無資料");
    }

    private String resolvePriceChangePercent(StockAnalysisContext context) {
        return context == null ? "暫無資料" : safeText(context.getPriceChangePercent(), "暫無資料");
    }

    private String resolveLatestVolume(StockAnalysisContext context) {
        if (context == null) {
            return "暫無資料";
        }

        String latestVolume = normalize(context.getLatestVolume());
        if (!latestVolume.isBlank()) {
            return latestVolume;
        }
        return safeText(context.getVolumeSummary(), "暫無資料");
    }

    private String resolveForeignInvestorSummary(StockAnalysisContext context) {
        return context == null ? "暫無外資資料" : safeText(context.getForeignInvestorSummary(), "暫無外資資料");
    }

    private String resolveMarginSummary(StockAnalysisContext context) {
        return context == null ? "暫無融資資料" : safeText(context.getMarginSummary(), "暫無融資資料");
    }

    private String resolveDisplayStance(AiAnalysisResult analysisResult) {
        String stance = analysisResult == null ? "" : normalize(analysisResult.getStance());
        if (stance.isBlank()) {
            return "中性";
        }

        String lowercase = stance.toLowerCase();
        if (lowercase.contains("bull") || stance.contains("偏多") || stance.contains("看多")) {
            return "偏多";
        }
        if (lowercase.contains("bear") || stance.contains("偏空") || stance.contains("看空")) {
            return "偏空";
        }
        if (lowercase.contains("neutral") || stance.contains("中立") || stance.contains("中性")) {
            return "中性";
        }
        return stance;
    }

    private String resolveSignedColor(String value) {
        String normalized = normalize(value);
        if (normalized.startsWith("+")) {
            return POSITIVE_COLOR;
        }
        if (normalized.startsWith("-")) {
            return NEGATIVE_COLOR;
        }
        return NEUTRAL_COLOR;
    }

    private String resolveStanceColor(String stance) {
        return switch (stance) {
            case "偏多" -> POSITIVE_COLOR;
            case "偏空" -> NEGATIVE_COLOR;
            default -> NEUTRAL_COLOR;
        };
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
