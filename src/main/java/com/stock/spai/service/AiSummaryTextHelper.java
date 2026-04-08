package com.stock.spai.service;

import java.util.Arrays;
import java.util.List;

/**
 * 集中處理 AI 回傳文字的摘要挑選與前言清理，
 * 讓卡片摘要優先呈現真正有判斷價值的句子。
 */
final class AiSummaryTextHelper {

    private static final List<String> GENERIC_TITLES = List.of(
            "分析摘要",
            "ai分析摘要",
            "摘要",
            "綜合判斷",
            "結論",
            "ai結論",
            "分析結論",
            "觀察重點",
            "重點整理",
            "投資建議"
    );

    private static final List<String> INSIGHT_KEYWORDS = List.of(
            "偏多",
            "偏空",
            "中性",
            "中立",
            "震盪",
            "整理",
            "支撐",
            "壓力",
            "量能",
            "趨勢",
            "買盤",
            "賣壓",
            "轉強",
            "轉弱",
            "回檔",
            "突破",
            "續強",
            "觀望",
            "風險",
            "上攻",
            "跌破",
            "籌碼",
            "外資",
            "融資"
    );

    private AiSummaryTextHelper() {
    }

    static String extractBestSummary(String rawText) {
        List<String> candidates = extractCandidateSentences(rawText);
        return candidates.stream()
                .filter(AiSummaryTextHelper::containsInsightKeyword)
                .findFirst()
                .orElseGet(() -> candidates.stream().findFirst().orElse(""));
    }

    static boolean isInformativeSummary(String text) {
        String sanitized = sanitizeSentence(text);
        if (sanitized.isBlank()) {
            return false;
        }

        String comparable = normalizeComparable(sanitized);
        if (GENERIC_TITLES.contains(comparable)) {
            return false;
        }

        if (comparable.length() <= 4) {
            return false;
        }

        return comparable.length() >= 8 || containsInsightKeyword(sanitized);
    }

    static boolean containsInsightKeyword(String text) {
        String comparable = normalizeComparable(text);
        return INSIGHT_KEYWORDS.stream().anyMatch(comparable::contains);
    }

    private static List<String> extractCandidateSentences(String rawText) {
        String normalized = normalize(rawText);
        if (normalized.isBlank()) {
            return List.of();
        }

        return normalized.lines()
                .map(String::trim)
                .map(AiSummaryTextHelper::removeMarkdownPrefix)
                .filter(line -> !line.isBlank())
                .flatMap(line -> splitSentences(line).stream())
                .map(AiSummaryTextHelper::sanitizeSentence)
                .filter(AiSummaryTextHelper::isInformativeSummary)
                .distinct()
                .toList();
    }

    private static List<String> splitSentences(String line) {
        return Arrays.stream(line.split("(?<=[。！？!?；;])"))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .toList();
    }

    private static String sanitizeSentence(String text) {
        String sanitized = removeMarkdownPrefix(normalize(text));
        String previous;

        do {
            previous = sanitized;
            sanitized = sanitized
                    .replaceFirst("^\\d+[.、)]\\s*", "")
                    .replaceFirst("^(AI\\s*)?(分析)?(摘要|結論|分析結論|AI結論|綜合判斷|觀察重點|重點整理|投資建議)\\s*[：:]\\s*", "")
                    .replaceFirst("^(好的|以下(?:為|是)?(?:本次)?(?:分析)?|綜合來看|整體來看|總結來說|簡單來說)\\s*[，、:：]?\\s*", "")
                    .replaceFirst("^(身為|作為)[^，。；:：]{0,40}[，、]\\s*", "")
                    .replaceFirst("^針對[^，。；:：]{0,20}[，、]\\s*", "")
                    .trim();
        } while (!sanitized.equals(previous));

        return sanitized;
    }

    private static String removeMarkdownPrefix(String text) {
        return normalize(text)
                .replaceFirst("^#+\\s*", "")
                .replaceFirst("^[\\-*]\\s*", "")
                .trim();
    }

    private static String normalizeComparable(String text) {
        return normalize(text)
                .toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。！？；：、「」『』（）()【】《》]+", "");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
