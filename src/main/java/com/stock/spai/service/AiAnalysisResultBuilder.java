package com.stock.spai.service;

import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.StockAnalysisContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 將 AI 原始輸出整理為較穩定可用的分析結果物件。
 */
@Component
public class AiAnalysisResultBuilder {

    private static final int SUMMARY_MAX_LENGTH = 160;

    private static final List<String> BULLISH_KEYWORDS = List.of(
            "偏多", "看多", "多方", "上攻", "續強", "轉強", "突破", "買盤", "量增", "站穩"
    );

    private static final List<String> BEARISH_KEYWORDS = List.of(
            "偏空", "看空", "空方", "轉弱", "跌破", "回檔", "賣壓", "量縮", "下彎", "風險"
    );

    /**
     * 依據 AI 原始文字建立分析結果，包含摘要與立場。
     */
    public AiAnalysisResult build(String symbol, String name, String rawText, StockAnalysisContext context) {
        String stance = detectStance(rawText);
        return new AiAnalysisResult(
                symbol,
                name,
                rawText,
                buildSummary(rawText, stance),
                stance,
                context
        );
    }

    /**
     * 從 AI 回覆中萃取適合 LINE 閱讀的 2 至 3 句短摘要。
     */
    String buildSummary(String rawText) {
        return buildSummary(rawText, detectStance(rawText));
    }

    private String buildSummary(String rawText, String stance) {
        String normalizedText = normalize(rawText);
        if (normalizedText.isEmpty()) {
            return "暫無 AI 分析摘要";
        }

        String structuredSummary = AiSummaryTextHelper.buildStructuredSummary(
                normalizedText,
                stance,
                3,
                SUMMARY_MAX_LENGTH
        );
        if (!structuredSummary.isBlank()) {
            return structuredSummary;
        }

        String bestSummary = AiSummaryTextHelper.extractBestSummary(normalizedText);
        if (bestSummary.isBlank()) {
            return trimToMaxLength(normalizedText, SUMMARY_MAX_LENGTH);
        }

        return trimToMaxLength(AiSummaryTextHelper.containsInsightKeyword(bestSummary)
                ? bestSummary
                : AiSummaryTextHelper.buildStructuredSummary(bestSummary, stance, 1, SUMMARY_MAX_LENGTH), SUMMARY_MAX_LENGTH);
    }

    /**
     * 依據關鍵字粗略判斷 AI 立場，供卡片快速閱讀使用。
     */
    String detectStance(String rawText) {
        String normalizedText = normalize(rawText);
        if (normalizedText.isEmpty()) {
            return "中性";
        }

        String explicitStance = AiSummaryTextHelper.extractExplicitStance(normalizedText);
        if (!explicitStance.isBlank()) {
            return explicitStance;
        }

        int bullishScore = countKeywords(normalizedText, BULLISH_KEYWORDS);
        int bearishScore = countKeywords(normalizedText, BEARISH_KEYWORDS);

        if (bullishScore > bearishScore) {
            return "偏多";
        }
        if (bearishScore > bullishScore) {
            return "偏空";
        }
        return "中性";
    }

    private int countKeywords(String text, List<String> keywords) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return keywords.stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .mapToInt(keyword -> normalized.contains(keyword) ? 1 : 0)
                .sum();
    }

    private String normalize(String rawText) {
        return rawText == null ? "" : rawText.trim();
    }

    private String trimToMaxLength(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
