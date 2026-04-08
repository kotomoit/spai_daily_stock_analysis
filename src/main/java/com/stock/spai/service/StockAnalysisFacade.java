package com.stock.spai.service;

import com.stock.spai.config.WatchlistConfigLoader;
import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.FinMindResponse;
import com.stock.spai.dto.StockAnalysisContext;
import com.stock.spai.dto.WatchlistConfig;
import com.stock.spai.dto.WatchlistItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 串接股票清單、資料來源與 AI 分析器，作為正式分析主流程的入口。
 */
@Service
@RequiredArgsConstructor
public class StockAnalysisFacade {

    private static final long DEFAULT_ANALYSIS_DAYS = 10L;

    private final WatchlistConfigLoader watchlistConfigLoader;
    private final FinMindService finMindService;
    private final StockAiAnalyzer stockAiAnalyzer;
    private final FinMindPromptSummaryBuilder finMindPromptSummaryBuilder;
    private final AiAnalysisResultBuilder aiAnalysisResultBuilder;

    /**
     * 使用預設起始日期分析所有啟用中的股票。
     */
    public List<AiAnalysisResult> analyzeEnabledStocks() {
        String defaultStartDate = LocalDate.now().minusDays(DEFAULT_ANALYSIS_DAYS).toString();
        return analyzeEnabledStocks(defaultStartDate);
    }

    /**
     * 依照指定起始日期分析所有啟用中的股票，並回傳分析結果集合。
     */
    public List<AiAnalysisResult> analyzeEnabledStocks(String startDate) {
        WatchlistConfig config = watchlistConfigLoader.load();
        if (config == null || config.getItems() == null || config.getItems().isEmpty()) {
            return Collections.emptyList();
        }

        return config.getItems().stream()
                .filter(Objects::nonNull)
                .filter(WatchlistItem::isEnabled)
                .map(item -> analyzeSingleStock(item, startDate))
                .toList();
    }

    /**
     * 逐支股票建立分析上下文並呼叫既有 AI 分析器。
     */
    public AiAnalysisResult analyzeSingleStock(WatchlistItem item, String startDate) {
        StockAnalysisContext context = buildAnalysisContext(item, startDate);
        String rawText = stockAiAnalyzer.analyzeSummary(context.getSymbol(), context.getPromptSummary());
        return aiAnalysisResultBuilder.build(
                context.getSymbol(),
                context.getName(),
                rawText,
                context
        );
    }

    /**
     * 預留未來串接 LINE 通知的擴充點，目前只負責回傳分析結果。
     */
    public List<AiAnalysisResult> analyzeEnabledStocksForNotification(String startDate) {
        return analyzeEnabledStocks(startDate);
    }

    private StockAnalysisContext buildAnalysisContext(WatchlistItem item, String startDate) {
        return Mono.zip(
                        finMindService.getStockPrice(item.getSymbol(), startDate),
                        finMindService.getInstitutionalInvestors(item.getSymbol(), startDate),
                        finMindService.getMarginData(item.getSymbol(), startDate)
                )
                .map(tuple -> buildContext(item, startDate, tuple.getT1(), tuple.getT2(), tuple.getT3()))
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("無法建立股票分析資料: " + item.getSymbol()));
    }

    private StockAnalysisContext buildContext(WatchlistItem item,
                                              String startDate,
                                              FinMindResponse priceResponse,
                                              FinMindResponse institutionalResponse,
                                              FinMindResponse marginResponse) {
        StockAnalysisContext context = new StockAnalysisContext();
        context.setSymbol(item.getSymbol());
        context.setName(item.getName());
        context.setStartDate(startDate);
        context.setPriceDataText(convertResponseDataToText(priceResponse));
        context.setInstitutionalDataText(convertResponseDataToText(institutionalResponse));
        context.setMarginDataText(convertResponseDataToText(marginResponse));
        context.setPromptSummary(finMindPromptSummaryBuilder.buildSummary(
                item.getSymbol(),
                item.getName(),
                startDate,
                priceResponse,
                institutionalResponse,
                marginResponse
        ));
        context.setLatestPriceSummary(buildLatestPriceSummary(priceResponse));
        context.setVolumeSummary(buildVolumeSummary(priceResponse));
        context.setLatestDataDate(extractLatestDataDate(priceResponse));
        context.setLatestClosePrice(buildLatestClosePrice(priceResponse));
        context.setPriceChange(buildPriceChange(priceResponse));
        context.setPriceChangePercent(buildPriceChangePercent(priceResponse));
        context.setLatestVolume(buildLatestVolume(priceResponse));
        context.setForeignInvestorSummary(buildForeignInvestorSummary(institutionalResponse));
        context.setMarginSummary(buildMarginSummary(marginResponse));
        return context;
    }

    /**
     * 保留原始字串資料，方便除錯與後續流程逐步銜接。
     */
    private String convertResponseDataToText(FinMindResponse response) {
        if (response == null || response.getData() == null) {
            return "[]";
        }
        return response.getData().toString();
    }

    private String buildLatestPriceSummary(FinMindResponse response) {
        FinMindResponse.StockData latest = findLatestPriceData(response);
        if (latest == null) {
            return "暫無最新收盤資料";
        }
        return safeText(latest.getDate()) + " 收盤 " + formatDecimal(latest.getClose());
    }

    private String buildVolumeSummary(FinMindResponse response) {
        FinMindResponse.StockData latest = findLatestPriceData(response);
        if (latest == null) {
            return "暫無成交量資料";
        }
        return safeText(latest.getDate()) + " 成交量 " + formatInteger(latest.getVol());
    }

    private String buildLatestClosePrice(FinMindResponse response) {
        FinMindResponse.StockData latest = findLatestPriceData(response);
        if (latest == null) {
            return "暫無資料";
        }
        return formatDecimal(latest.getClose());
    }

    private String buildPriceChange(FinMindResponse response) {
        List<FinMindResponse.StockData> sortedData = sortByDate(response);
        if (sortedData.size() < 2) {
            return "暫無前一日資料";
        }

        FinMindResponse.StockData latest = sortedData.get(sortedData.size() - 1);
        FinMindResponse.StockData previous = sortedData.get(sortedData.size() - 2);
        return formatSignedDecimal(latest.getClose() - previous.getClose());
    }

    private String buildPriceChangePercent(FinMindResponse response) {
        List<FinMindResponse.StockData> sortedData = sortByDate(response);
        if (sortedData.size() < 2) {
            return "暫無前一日資料";
        }

        FinMindResponse.StockData latest = sortedData.get(sortedData.size() - 1);
        FinMindResponse.StockData previous = sortedData.get(sortedData.size() - 2);
        if (previous.getClose() == 0D) {
            return "暫無前一日資料";
        }

        double changePercent = ((latest.getClose() - previous.getClose()) / previous.getClose()) * 100D;
        return formatSignedPercent(changePercent);
    }

    private String buildLatestVolume(FinMindResponse response) {
        FinMindResponse.StockData latest = findLatestPriceData(response);
        if (latest == null) {
            return "暫無資料";
        }
        return formatInteger(latest.getVol());
    }

    private String buildForeignInvestorSummary(FinMindResponse response) {
        List<FinMindResponse.StockData> latestRows = findLatestRows(response);
        if (latestRows.isEmpty()) {
            return "暫無外資資料";
        }

        List<FinMindResponse.StockData> foreignRows = latestRows.stream()
                .filter(this::isForeignInvestor)
                .toList();
        if (foreignRows.isEmpty()) {
            return "最新資料無外資紀錄";
        }

        long netBuySell = foreignRows.stream()
                .mapToLong(item -> item.getBuy() - item.getSell())
                .sum();
        return "買賣超 " + formatSignedInteger(netBuySell);
    }

    private String buildMarginSummary(FinMindResponse response) {
        List<FinMindResponse.StockData> sortedData = sortByDate(response);
        if (sortedData.isEmpty()) {
            return "暫無融資資料";
        }

        FinMindResponse.StockData latest = sortedData.get(sortedData.size() - 1);
        String previousDayChange = "";
        if (sortedData.size() >= 2) {
            FinMindResponse.StockData previous = sortedData.get(sortedData.size() - 2);
            previousDayChange = "，較前日 " + formatSignedInteger(latest.getMarginTotal() - previous.getMarginTotal());
        }

        return "買進 " + formatInteger(latest.getMarginBuy())
                + "，餘額 " + formatInteger(latest.getMarginTotal())
                + previousDayChange;
    }

    private String extractLatestDataDate(FinMindResponse response) {
        FinMindResponse.StockData latest = findLatestPriceData(response);
        return latest == null ? "" : safeText(latest.getDate());
    }

    private FinMindResponse.StockData findLatestPriceData(FinMindResponse response) {
        return sortByDate(response).stream()
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private List<FinMindResponse.StockData> findLatestRows(FinMindResponse response) {
        List<FinMindResponse.StockData> sortedData = sortByDate(response);
        if (sortedData.isEmpty()) {
            return List.of();
        }

        String latestDate = safeText(sortedData.get(sortedData.size() - 1).getDate());
        return sortedData.stream()
                .filter(item -> latestDate.equals(safeText(item.getDate())))
                .toList();
    }

    private List<FinMindResponse.StockData> sortByDate(FinMindResponse response) {
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            return List.of();
        }

        return response.getData().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(stockData -> safeText(stockData.getDate())))
                .toList();
    }

    private boolean isForeignInvestor(FinMindResponse.StockData stockData) {
        String type = safeText(stockData.getType()).toLowerCase(Locale.ROOT);
        return type.contains("外資")
                || type.contains("外陸資")
                || type.contains("foreign");
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private String formatDecimal(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String formatSignedDecimal(double value) {
        return String.format(Locale.US, "%+.2f", value);
    }

    private String formatSignedPercent(double value) {
        return String.format(Locale.US, "%+.2f%%", value);
    }

    private String formatInteger(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private String formatSignedInteger(long value) {
        return String.format(Locale.US, "%+,d", value);
    }
}
