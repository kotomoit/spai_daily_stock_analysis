package com.stock.spai.runner;

import com.stock.spai.config.StockAnalysisLineNotifyProperties;
import com.stock.spai.config.StockAnalysisReportProperties;
import com.stock.spai.config.StockAnalysisRunnerProperties;
import com.stock.spai.config.WatchlistConfigLoader;
import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.WatchlistConfig;
import com.stock.spai.dto.WatchlistItem;
import com.stock.spai.service.AnalysisReportWriter;
import com.stock.spai.service.LineMessagingService;
import com.stock.spai.service.StockAnalysisFacade;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 手動執行股票分析流程的啟動器。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "stock.analysis.runner", name = "enabled", havingValue = "true")
public class StockAnalysisApplicationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockAnalysisApplicationRunner.class);

    private final WatchlistConfigLoader watchlistConfigLoader;
    private final StockAnalysisFacade stockAnalysisFacade;
    private final StockAnalysisRunnerProperties runnerProperties;
    private final AnalysisReportWriter analysisReportWriter;
    private final StockAnalysisReportProperties reportProperties;
    private final LineMessagingService lineMessagingService;
    private final StockAnalysisLineNotifyProperties lineNotifyProperties;

    @Override
    public void run(ApplicationArguments args) {
        List<WatchlistItem> enabledItems = loadEnabledItems();
        if (enabledItems.isEmpty()) {
            log.warn("手動分析 runner 啟動時未找到 watchlist 中啟用的股票，略過本次執行。");
            return;
        }

        String stockLabels = enabledItems.stream()
                .map(this::toStockLabel)
                .collect(Collectors.joining(", "));
        String startDateForLog = resolveStartDateForLog();

        log.info("手動分析 runner 開始執行，股票清單：{}，起始日期：{}", stockLabels, startDateForLog);

        try {
            List<AiAnalysisResult> results = executeAnalysis();
            if (results.isEmpty()) {
                log.warn("手動分析 runner 已完成分析，但沒有可輸出的結果。");
                return;
            }

            results.forEach(this::logAnalysisResult);
            writeReportIfEnabled(results);
            sendLineNotificationsIfEnabled(results);

            log.info("手動分析 runner 已完成，共輸出 {} 筆分析結果。", results.size());
        } catch (Exception exception) {
            log.error("手動分析 runner 執行失敗，股票清單：{}，起始日期：{}", stockLabels, startDateForLog, exception);
            throw new IllegalStateException("手動分析 runner 執行失敗", exception);
        }
    }

    private List<AiAnalysisResult> executeAnalysis() {
        if (StringUtils.hasText(runnerProperties.getStartDate())) {
            return stockAnalysisFacade.analyzeEnabledStocks(runnerProperties.getStartDate());
        }
        return stockAnalysisFacade.analyzeEnabledStocks();
    }

    private List<WatchlistItem> loadEnabledItems() {
        WatchlistConfig config = watchlistConfigLoader.load();
        if (config == null || config.getItems() == null) {
            return List.of();
        }

        return config.getItems().stream()
                .filter(Objects::nonNull)
                .filter(WatchlistItem::isEnabled)
                .toList();
    }

    private void logAnalysisResult(AiAnalysisResult result) {
        log.info(
                "分析結果：{} | 立場：{} | 摘要：{}",
                toResultLabel(result),
                safeText(result == null ? null : result.getStance()),
                extractSummary(result)
        );
    }

    private void writeReportIfEnabled(List<AiAnalysisResult> results) {
        if (!reportProperties.isEnabled()) {
            log.info("手動分析 runner 已停用 JSON 報告輸出。");
            return;
        }

        analysisReportWriter.write(results)
                .ifPresent(this::logReportPath);
    }

    private void sendLineNotificationsIfEnabled(List<AiAnalysisResult> results) {
        if (!lineNotifyProperties.isEnabled()) {
            log.info("手動分析 runner 已停用 LINE 發送，因為 stock.analysis.line-notify.enabled=false。");
            return;
        }

        for (AiAnalysisResult result : results) {
            lineMessagingService.sendProfessionalFlex(result).block();
            log.info("手動分析 runner 已送出 LINE 通知：{}", toResultLabel(result));
        }
    }

    private String resolveStartDateForLog() {
        if (StringUtils.hasText(runnerProperties.getStartDate())) {
            return runnerProperties.getStartDate();
        }
        return "沿用 StockAnalysisFacade 預設區間";
    }

    private String toStockLabel(WatchlistItem item) {
        if (item == null) {
            return "未提供股票資料";
        }
        return safeText(item.getSymbol()) + " " + safeText(item.getName());
    }

    private String toResultLabel(AiAnalysisResult result) {
        if (result == null) {
            return "未提供分析結果";
        }
        return safeText(result.getSymbol()) + " " + safeText(result.getName());
    }

    private String extractSummary(AiAnalysisResult result) {
        if (result == null) {
            return "未提供摘要";
        }

        String summary = StringUtils.hasText(result.getSummary()) ? result.getSummary() : result.getRawText();
        return safeText(summary).replaceAll("\\s+", " ").trim();
    }

    private String safeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "未提供";
        }
        return value;
    }

    private void logReportPath(Path reportPath) {
        log.info("手動分析 runner 已輸出 JSON 報告：{}", reportPath);
    }
}
