package com.stock.spai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 提供外部保存與靜態頁展示使用的分析報告資料。
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnalysisReport(
        String generatedAt,
        String reportDate,
        int resultCount,
        List<AnalysisReportItem> items
) {

    public static AnalysisReport from(List<AiAnalysisResult> results, ZonedDateTime generatedAt) {
        List<AnalysisReportItem> mappedItems = results == null ? List.of() : results.stream()
                .filter(Objects::nonNull)
                .map(AnalysisReportItem::from)
                .toList();

        return new AnalysisReport(
                generatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                generatedAt.toLocalDate().toString(),
                mappedItems.size(),
                mappedItems
        );
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AnalysisReportItem(
            String symbol,
            String name,
            String summary,
            String stance,
            String rawText,
            AnalysisReportContext context
    ) {

        private static AnalysisReportItem from(AiAnalysisResult result) {
            return new AnalysisReportItem(
                    safeText(result.getSymbol()),
                    safeText(result.getName()),
                    resolveSummary(result),
                    safeText(result.getStance()),
                    safeText(result.getRawText()),
                    AnalysisReportContext.from(result.getContext())
            );
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AnalysisReportContext(
            String startDate,
            String latestDataDate,
            String latestClosePrice,
            String priceChange,
            String priceChangePercent,
            String latestVolume,
            String foreignInvestorSummary,
            String marginSummary,
            String latestPriceSummary,
            String volumeSummary,
            String promptSummary,
            String priceDataText,
            String institutionalDataText,
            String marginDataText
    ) {

        private static AnalysisReportContext from(StockAnalysisContext context) {
            if (context == null) {
                return null;
            }

            return new AnalysisReportContext(
                    safeText(context.getStartDate()),
                    safeText(context.getLatestDataDate()),
                    safeText(context.getLatestClosePrice()),
                    safeText(context.getPriceChange()),
                    safeText(context.getPriceChangePercent()),
                    safeText(context.getLatestVolume()),
                    safeText(context.getForeignInvestorSummary()),
                    safeText(context.getMarginSummary()),
                    safeText(context.getLatestPriceSummary()),
                    safeText(context.getVolumeSummary()),
                    safeText(context.getPromptSummary()),
                    safeText(context.getPriceDataText()),
                    safeText(context.getInstitutionalDataText()),
                    safeText(context.getMarginDataText())
            );
        }
    }

    private static String resolveSummary(AiAnalysisResult result) {
        if (result == null) {
            return "";
        }

        if (result.getSummary() != null && !result.getSummary().isBlank()) {
            return result.getSummary();
        }
        return safeText(result.getRawText());
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }
}
