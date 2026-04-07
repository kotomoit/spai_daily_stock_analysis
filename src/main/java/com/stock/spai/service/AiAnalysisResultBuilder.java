package com.stock.spai.service;

import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.StockAnalysisContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 以低侵入方式將 AI 原始文字補強為較結構化的分析結果。
 */
@Component
public class AiAnalysisResultBuilder {

    private static final int SUMMARY_MAX_LENGTH = 120;

    private static final List<String> BULLISH_KEYWORDS = List.of(
            "偏多", "看多", "多方", "買進", "買入", "佈局", "樂觀", "上漲", "強勢"
    );

    private static final List<String> BEARISH_KEYWORDS = List.of(
            "偏空", "看空", "空方", "賣出", "減碼", "保守", "下跌", "弱勢", "風險"
    );

    /**
     * 建立第一期的結構化結果，保留 rawText 並補上保守推論出的摘要與立場。
     */
    public AiAnalysisResult build(String symbol, String name, String rawText, StockAnalysisContext context) {
        return new AiAnalysisResult(
                symbol,
                name,
                rawText,
                buildSummary(rawText),
                detectStance(rawText),
                context
        );
    }

    /**
     * 先取第一個有內容的段落作為摘要，若過長則保守截斷。
     */
    String buildSummary(String rawText) {
        String normalizedText = normalize(rawText);
        if (normalizedText.isEmpty()) {
            return "尚無 AI 分析摘要。";
        }

        String firstMeaningfulLine = Arrays.stream(normalizedText.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(this::removeMarkdownPrefix)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse(normalizedText);

        if (firstMeaningfulLine.length() <= SUMMARY_MAX_LENGTH) {
            return firstMeaningfulLine;
        }
        return firstMeaningfulLine.substring(0, SUMMARY_MAX_LENGTH) + "...";
    }

    /**
     * 以關鍵字數量做保守判斷，若訊號不明確則回傳中立。
     */
    String detectStance(String rawText) {
        String normalizedText = normalize(rawText);
        if (normalizedText.isEmpty()) {
            return "中立";
        }

        int bullishScore = countKeywords(normalizedText, BULLISH_KEYWORDS);
        int bearishScore = countKeywords(normalizedText, BEARISH_KEYWORDS);

        if (bullishScore > bearishScore) {
            return "偏多";
        }
        if (bearishScore > bullishScore) {
            return "偏空";
        }
        return "中立";
    }

    private int countKeywords(String text, List<String> keywords) {
        return keywords.stream()
                .mapToInt(keyword -> text.contains(keyword) ? 1 : 0)
                .sum();
    }

    private String normalize(String rawText) {
        return rawText == null ? "" : rawText.trim();
    }

    private String removeMarkdownPrefix(String line) {
        return line.replaceFirst("^#+\\s*", "")
                .replaceFirst("^[\\-*]\\s*", "")
                .trim();
    }
}
