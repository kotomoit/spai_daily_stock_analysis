package com.stock.spai.service;

import com.stock.spai.dto.FinMindResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 將 FinMind 原始回傳資料整理成較適合 LLM 使用的繁體中文摘要。
 */
@Component
public class FinMindPromptSummaryBuilder {

    /**
     * 建立單一股票的綜合摘要，供 AI 分析流程直接使用。
     */
    public String buildSummary(String symbol,
                               String name,
                               String industryCategory,
                               String startDate,
                               FinMindResponse priceResponse,
                               FinMindResponse institutionalResponse,
                               FinMindResponse marginResponse) {
        return String.join("\n",
                "股票代號：" + safeText(symbol),
                "股票名稱：" + safeText(name),
                "產業／板塊：" + safeText(industryCategory),
                "分析起始日：" + safeText(startDate),
                buildPriceSummary(priceResponse),
                buildInstitutionalSummary(institutionalResponse),
                buildMarginSummary(marginResponse)
        );
    }

    private String buildPriceSummary(FinMindResponse response) {
        List<FinMindResponse.StockData> data = safeData(response);
        if (data.isEmpty()) {
            return "價格資料重點：無可用價格資料。";
        }

        FinMindResponse.StockData latest = data.get(data.size() - 1);
        double highestPrice = data.stream()
                .mapToDouble(FinMindResponse.StockData::getMax)
                .max()
                .orElse(0D);
        double lowestPrice = data.stream()
                .mapToDouble(FinMindResponse.StockData::getMin)
                .min()
                .orElse(0D);
        long totalVolume = data.stream()
                .mapToLong(FinMindResponse.StockData::getVol)
                .sum();

        return String.join("\n",
                "價格資料重點：",
                "- 最新交易日：" + safeText(latest.getDate()),
                "- 最新開高低收：" + formatDecimal(latest.getOpen()) + " / "
                        + formatDecimal(latest.getMax()) + " / "
                        + formatDecimal(latest.getMin()) + " / "
                        + formatDecimal(latest.getClose()),
                "- 區間最高價 / 最低價：" + formatDecimal(highestPrice) + " / " + formatDecimal(lowestPrice),
                "- 區間總成交量：" + totalVolume
        );
    }

    private String buildInstitutionalSummary(FinMindResponse response) {
        List<FinMindResponse.StockData> data = safeData(response);
        if (data.isEmpty()) {
            return "法人資料重點：無可用法人資料。";
        }

        String latestDate = data.stream()
                .map(FinMindResponse.StockData::getDate)
                .filter(this::hasText)
                .max(Comparator.naturalOrder())
                .orElse("未知");

        List<String> latestLines = data.stream()
                .filter(item -> latestDate.equals(item.getDate()))
                .map(item -> safeText(item.getType()) + " 買賣超 " + (item.getBuy() - item.getSell()))
                .collect(Collectors.toList());

        long totalNetBuySell = data.stream()
                .mapToLong(item -> item.getBuy() - item.getSell())
                .sum();

        String latestSummary = latestLines.isEmpty()
                ? "- 最新法人分點：無可用資料"
                : "- 最新法人分點：" + String.join("；", latestLines);

        return String.join("\n",
                "法人資料重點：",
                "- 最新資料日期：" + latestDate,
                latestSummary,
                "- 區間合計買賣超：" + totalNetBuySell
        );
    }

    private String buildMarginSummary(FinMindResponse response) {
        List<FinMindResponse.StockData> data = safeData(response);
        if (data.isEmpty()) {
            return "融資資料重點：無可用融資資料。";
        }

        FinMindResponse.StockData first = data.get(0);
        FinMindResponse.StockData latest = data.get(data.size() - 1);
        long marginTotalChange = latest.getMarginTotal() - first.getMarginTotal();

        return String.join("\n",
                "融資資料重點：",
                "- 最新資料日期：" + safeText(latest.getDate()),
                "- 最新融資買進：" + latest.getMarginBuy(),
                "- 最新融資餘額：" + latest.getMarginTotal(),
                "- 區間融資餘額變化：" + marginTotalChange
        );
    }

    private List<FinMindResponse.StockData> safeData(FinMindResponse response) {
        if (response == null || response.getData() == null) {
            return List.of();
        }
        return response.getData();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeText(String value) {
        return hasText(value) ? value : "目前未提供相關資料";
    }

    private String formatDecimal(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
