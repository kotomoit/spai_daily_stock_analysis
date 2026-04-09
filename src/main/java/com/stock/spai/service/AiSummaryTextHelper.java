package com.stock.spai.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 集中處理 AI 回傳文字的摘要挑選、章節擷取與 markdown 清理，
 * 讓 LINE 卡片摘要優先呈現有結論、有理由、有風險提醒的短摘要。
 */
final class AiSummaryTextHelper {

    private static final Pattern SECTION_TITLE_PATTERN = Pattern.compile("^#{2,6}\\s*(.+?)\\s*$");
    private static final Pattern EXPLICIT_STANCE_PATTERN = Pattern.compile("結論\\s*[：:]\\s*(偏多|中性|偏空|觀望)");

    private static final List<String> SUMMARY_SECTION_TITLES = List.of(
            "綜合建議",
            "技術面總結",
            "風險提示"
    );

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
            "投資建議",
            "今日重點",
            "籌碼資金面觀察",
            "技術面總結",
            "消息面情報",
            "財報概要",
            "基本面",
            "綜合建議",
            "風險提示"
    );

    private static final List<String> INSIGHT_KEYWORDS = List.of(
            "偏多",
            "偏空",
            "中性",
            "觀望",
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
            "風險",
            "上攻",
            "跌破",
            "失守",
            "籌碼",
            "外資",
            "融資"
    );

    private static final List<String> BULLISH_KEYWORDS = List.of(
            "偏多", "看多", "多方", "上攻", "續強", "轉強", "突破", "買盤", "量增", "站穩", "支撐"
    );

    private static final List<String> BEARISH_KEYWORDS = List.of(
            "偏空", "看空", "空方", "轉弱", "跌破", "回檔", "賣壓", "量縮", "下彎", "失守", "壓力"
    );

    private static final List<String> NEUTRAL_KEYWORDS = List.of(
            "中性", "震盪", "整理", "區間", "盤整", "觀察", "拉鋸"
    );

    private static final List<String> RISK_HINT_KEYWORDS = List.of(
            "風險", "留意", "注意", "提防", "若", "一旦", "失守", "跌破", "轉弱", "回落"
    );

    private AiSummaryTextHelper() {
    }

    static String buildStructuredSummary(String rawText, String stance, int maxSentences, int maxLength) {
        String normalizedText = normalize(rawText);
        if (normalizedText.isBlank()) {
            return "";
        }

        Map<String, String> sections = extractSections(normalizedText);
        String resolvedStance = normalize(stance);
        List<String> summarySentences = new ArrayList<>();

        appendDistinct(summarySentences, buildConclusionSentence(sections.get("綜合建議"), resolvedStance));
        appendDistinct(summarySentences, extractPreferredSentence(sections.get("技術面總結"), resolvedStance, false));
        appendDistinct(summarySentences, buildRiskSentence(sections.get("風險提示")));

        if (summarySentences.size() < 2) {
            for (String sectionTitle : SUMMARY_SECTION_TITLES) {
                appendDistinct(summarySentences, extractPreferredSentence(
                        sections.get(sectionTitle),
                        resolvedStance,
                        "風險提示".equals(sectionTitle)
                ));
                if (summarySentences.size() >= maxSentences) {
                    break;
                }
            }
        }

        if (summarySentences.size() < 2) {
            for (String candidate : extractCandidateSentences(normalizedText)) {
                if (!isSentenceCompatibleWithStance(candidate, resolvedStance) && !summarySentences.isEmpty()) {
                    continue;
                }
                appendDistinct(summarySentences, ensureSentenceEnding(candidate));
                if (summarySentences.size() >= maxSentences) {
                    break;
                }
            }
        }

        return joinSummary(summarySentences, maxSentences, maxLength);
    }

    static String extractBestSummary(String rawText) {
        List<String> candidates = extractCandidateSentences(rawText);
        return candidates.stream()
                .filter(AiSummaryTextHelper::containsInsightKeyword)
                .findFirst()
                .orElseGet(() -> candidates.stream().findFirst().orElse(""));
    }

    static String extractExplicitStance(String rawText) {
        String normalizedText = normalize(rawText);
        if (normalizedText.isBlank()) {
            return "";
        }

        Matcher matcher = EXPLICIT_STANCE_PATTERN.matcher(stripMarkdownDecorations(normalizedText));
        return matcher.find() ? matcher.group(1) : "";
    }

    static boolean isInformativeSummary(String text) {
        String sanitized = sanitizeSentence(text);
        if (sanitized.isBlank()) {
            return false;
        }

        String comparable = normalizeComparable(sanitized);
        if (GENERIC_TITLES.stream().map(AiSummaryTextHelper::normalizeComparable).anyMatch(comparable::equals)) {
            return false;
        }

        if (comparable.length() <= 4) {
            return false;
        }

        return comparable.length() >= 8 || containsInsightKeyword(sanitized);
    }

    static boolean containsInsightKeyword(String text) {
        String comparable = normalizeComparable(text);
        return INSIGHT_KEYWORDS.stream()
                .map(AiSummaryTextHelper::normalizeComparable)
                .anyMatch(comparable::contains);
    }

    private static String buildConclusionSentence(String sectionText, String stance) {
        String normalizedSection = normalize(sectionText);
        if (normalizedSection.isBlank()) {
            return "";
        }

        String explicitStance = extractExplicitStance(normalizedSection);
        String resolvedStance = explicitStance.isBlank() ? normalize(stance) : explicitStance;

        List<String> candidates = extractCandidateSentences(normalizedSection).stream()
                .filter(sentence -> !normalizeComparable(sentence).equals(normalizeComparable(resolvedStance)))
                .filter(sentence -> isSentenceCompatibleWithStance(sentence, resolvedStance))
                .toList();

        String reasonSentence = candidates.stream().findFirst().orElse("");
        if (!resolvedStance.isBlank()) {
            if (reasonSentence.isBlank()) {
                return ensureSentenceEnding("結論" + resolvedStance);
            }

            String cleanedReason = stripLeadingConclusionText(reasonSentence);
            if (cleanedReason.isBlank()) {
                return ensureSentenceEnding("結論" + resolvedStance);
            }
            return ensureSentenceEnding("結論" + resolvedStance + "，" + cleanedReason);
        }

        return extractPreferredSentence(normalizedSection, stance, false);
    }

    private static String buildRiskSentence(String sectionText) {
        String riskSentence = extractPreferredSentence(sectionText, "", true);
        if (riskSentence.isBlank()) {
            return "";
        }
        if (containsAnyKeyword(riskSentence, RISK_HINT_KEYWORDS) || riskSentence.startsWith("風險上")) {
            return riskSentence;
        }
        return ensureSentenceEnding("風險上，" + stripLeadingConnector(riskSentence));
    }

    private static String extractPreferredSentence(String sectionText, String stance, boolean riskSection) {
        List<String> candidates = extractCandidateSentences(sectionText);
        if (candidates.isEmpty()) {
            return "";
        }

        String selected = candidates.stream()
                .filter(sentence -> riskSection || isSentenceCompatibleWithStance(sentence, stance))
                .findFirst()
                .orElse(candidates.getFirst());

        if (riskSection) {
            return ensureSentenceEnding(selected);
        }
        return ensureSentenceEnding(selected);
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
        List<String> sentences = Arrays.stream(line.split("(?<=[。！？!?；;])"))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .toList();
        return sentences.isEmpty() ? List.of(line.trim()) : sentences;
    }

    private static Map<String, String> extractSections(String rawText) {
        LinkedHashMap<String, StringBuilder> sectionBuilders = new LinkedHashMap<>();
        String currentSection = "";

        for (String line : normalize(rawText).replace("\r\n", "\n").split("\n")) {
            String trimmedLine = line.trim();
            Matcher matcher = SECTION_TITLE_PATTERN.matcher(trimmedLine);
            if (matcher.matches()) {
                currentSection = normalizeSectionTitle(matcher.group(1));
                sectionBuilders.putIfAbsent(currentSection, new StringBuilder());
                continue;
            }

            if (!currentSection.isBlank()) {
                sectionBuilders.get(currentSection).append(line).append('\n');
            }
        }

        Map<String, String> sections = new LinkedHashMap<>();
        sectionBuilders.forEach((title, builder) -> sections.put(title, builder.toString().trim()));
        return sections;
    }

    private static String normalizeSectionTitle(String title) {
        return stripMarkdownDecorations(title)
                .replaceAll("[：:]+$", "")
                .trim();
    }

    private static String sanitizeSentence(String text) {
        String sanitized = stripMarkdownDecorations(removeMarkdownPrefix(normalize(text)));
        String previous;

        do {
            previous = sanitized;
            sanitized = sanitized
                    .replaceFirst("^\\d+[.、)]\\s*", "")
                    .replaceFirst("^(AI\\s*)?(分析)?(摘要|結論|分析結論|AI結論|綜合判斷|觀察重點|重點整理|投資建議|今日重點|技術面總結|消息面情報|財報概要|基本面|綜合建議|風險提示|籌碼(?:／|/)?資金面觀察)\\s*[：:]\\s*", "")
                    .replaceFirst("^(好的|以下(?:為|是)?(?:本次)?(?:分析)?(?:摘要|結果)?|綜合來看|整體來看|總結來說|簡單來說|身為分析師|作為投資判斷參考)\\s*[，、:：]?\\s*", "")
                    .replaceFirst("^(身為|作為)[^，。；:：]{0,40}[，、]\\s*", "")
                    .replaceFirst("^針對[^，。；:：]{0,20}[，、]\\s*", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        } while (!sanitized.equals(previous));

        return sanitized;
    }

    private static String removeMarkdownPrefix(String text) {
        return normalize(text)
                .replaceFirst("^#+\\s*", "")
                .replaceFirst("^>\\s*", "")
                .replaceFirst("^[\\-*•]\\s*", "")
                .trim();
    }

    private static String stripMarkdownDecorations(String text) {
        return normalize(text)
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("*", "")
                .replace("_", "")
                .replace("~~", "")
                .replace("[", "")
                .replace("]", "")
                .trim();
    }

    private static String stripLeadingConclusionText(String text) {
        return sanitizeSentence(text)
                .replaceFirst("^(結論\\s*[：:]\\s*)?(偏多|中性|偏空|觀望)\\s*[，、:：]?\\s*", "")
                .trim();
    }

    private static String stripLeadingConnector(String text) {
        return sanitizeSentence(text)
                .replaceFirst("^(另外|此外|同時|但|不過)\\s*[，、]?\\s*", "")
                .trim();
    }

    private static String joinSummary(List<String> sentences, int maxSentences, int maxLength) {
        List<String> limitedSentences = new ArrayList<>();
        int currentLength = 0;

        for (String sentence : sentences) {
            if (limitedSentences.size() >= maxSentences) {
                break;
            }

            String normalizedSentence = ensureSentenceEnding(sentence);
            int extraLength = limitedSentences.isEmpty() ? normalizedSentence.length() : normalizedSentence.length() + 1;
            if (!limitedSentences.isEmpty() && currentLength + extraLength > maxLength) {
                break;
            }

            if (limitedSentences.isEmpty() && normalizedSentence.length() > maxLength) {
                return truncate(normalizedSentence, maxLength);
            }

            limitedSentences.add(normalizedSentence);
            currentLength += extraLength;
        }

        if (limitedSentences.isEmpty()) {
            return "";
        }
        return String.join(" ", limitedSentences);
    }

    private static void appendDistinct(List<String> sentences, String candidate) {
        String normalizedCandidate = ensureSentenceEnding(candidate);
        if (!isInformativeSummary(normalizedCandidate)) {
            return;
        }

        String comparableCandidate = normalizeComparable(normalizedCandidate);
        boolean duplicated = sentences.stream()
                .map(AiSummaryTextHelper::normalizeComparable)
                .anyMatch(comparableCandidate::equals);
        if (!duplicated) {
            sentences.add(normalizedCandidate);
        }
    }

    private static boolean isSentenceCompatibleWithStance(String sentence, String stance) {
        String normalizedStance = normalize(stance);
        if (normalizedStance.isBlank()) {
            return true;
        }

        int bullishScore = countKeywords(sentence, BULLISH_KEYWORDS);
        int bearishScore = countKeywords(sentence, BEARISH_KEYWORDS);
        int neutralScore = countKeywords(sentence, NEUTRAL_KEYWORDS);

        return switch (normalizedStance) {
            case "偏多" -> bearishScore == 0 || bullishScore >= bearishScore;
            case "偏空" -> bullishScore == 0 || bearishScore >= bullishScore;
            case "觀望" -> neutralScore > 0 || (bullishScore == 0 && bearishScore == 0);
            case "中性" -> neutralScore > 0 || Math.abs(bullishScore - bearishScore) <= 1 || (bullishScore > 0 && bearishScore > 0);
            default -> true;
        };
    }

    private static boolean containsAnyKeyword(String text, List<String> keywords) {
        String comparable = normalizeComparable(text);
        return keywords.stream()
                .map(AiSummaryTextHelper::normalizeComparable)
                .anyMatch(comparable::contains);
    }

    private static int countKeywords(String text, List<String> keywords) {
        String comparable = normalizeComparable(text);
        return (int) keywords.stream()
                .map(AiSummaryTextHelper::normalizeComparable)
                .filter(comparable::contains)
                .count();
    }

    private static String ensureSentenceEnding(String text) {
        String sanitized = sanitizeSentence(text);
        if (sanitized.isBlank()) {
            return "";
        }
        if (sanitized.matches(".*[。！？!?；;]$")) {
            return sanitized;
        }
        return sanitized + "。";
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private static String normalizeComparable(String text) {
        return stripMarkdownDecorations(normalize(text))
                .toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。！？；：、「」『』（）()【】《》]+", "");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
