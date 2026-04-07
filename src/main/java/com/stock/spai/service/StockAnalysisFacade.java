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
import java.util.Comparator;
import java.util.Collections;
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
     * 預留未來串接 LINE 通知的擴充點，目前第一版只負責回傳分析結果。
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
        String latestPriceSummary = buildLatestPriceSummary(priceResponse);
        String volumeSummary = buildVolumeSummary(priceResponse);
        String latestDataDate = extractLatestDataDate(priceResponse);

        return new StockAnalysisContext(
                item.getSymbol(),
                item.getName(),
                startDate,
                convertResponseDataToText(priceResponse),
                convertResponseDataToText(institutionalResponse),
                convertResponseDataToText(marginResponse),
                finMindPromptSummaryBuilder.buildSummary(
                        item.getSymbol(),
                        item.getName(),
                        startDate,
                        priceResponse,
                        institutionalResponse,
                        marginResponse
                ),
                latestPriceSummary,
                volumeSummary,
                latestDataDate
        );
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
        return safeText(latest.getDate()) + " 成交量 " + String.format(Locale.US, "%,d", latest.getVol());
    }

    private String extractLatestDataDate(FinMindResponse response) {
        FinMindResponse.StockData latest = findLatestPriceData(response);
        return latest == null ? "" : safeText(latest.getDate());
    }

    private FinMindResponse.StockData findLatestPriceData(FinMindResponse response) {
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            return null;
        }

        return response.getData().stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(
                        stockData -> safeText(stockData.getDate())
                ))
                .orElse(null);
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private String formatDecimal(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
