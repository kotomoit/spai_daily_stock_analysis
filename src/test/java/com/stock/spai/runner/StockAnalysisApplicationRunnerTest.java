package com.stock.spai.runner;

import com.stock.spai.config.StockAnalysisReportProperties;
import com.stock.spai.config.StockAnalysisRunnerProperties;
import com.stock.spai.config.WatchlistConfigLoader;
import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.WatchlistConfig;
import com.stock.spai.dto.WatchlistItem;
import com.stock.spai.service.AnalysisReportWriter;
import com.stock.spai.service.StockAnalysisFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@Tag("unit")
class StockAnalysisApplicationRunnerTest {

    @Mock
    private WatchlistConfigLoader watchlistConfigLoader;

    @Mock
    private StockAnalysisFacade stockAnalysisFacade;

    @Mock
    private AnalysisReportWriter analysisReportWriter;

    private StockAnalysisRunnerProperties runnerProperties;
    private StockAnalysisReportProperties reportProperties;
    private StockAnalysisApplicationRunner runner;

    @BeforeEach
    void setUp() {
        runnerProperties = new StockAnalysisRunnerProperties();
        reportProperties = new StockAnalysisReportProperties();
        runner = new StockAnalysisApplicationRunner(
                watchlistConfigLoader,
                stockAnalysisFacade,
                runnerProperties,
                analysisReportWriter,
                reportProperties
        );
    }

    @Test
    void run_shouldSkipWhenNoEnabledStocks(CapturedOutput output) throws Exception {
        WatchlistItem disabledItem = new WatchlistItem();
        disabledItem.setSymbol("2317");
        disabledItem.setName("鴻海");
        disabledItem.setEnabled(false);

        WatchlistConfig config = new WatchlistConfig();
        config.setItems(List.of(disabledItem));

        when(watchlistConfigLoader.load()).thenReturn(config);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(stockAnalysisFacade, never()).analyzeEnabledStocks();
        verify(stockAnalysisFacade, never()).analyzeEnabledStocks("2026-04-01");
        assertThat(output).contains("未找到 watchlist 中啟用的股票");
    }

    @Test
    void run_shouldUseConfiguredStartDateAndLogResults(CapturedOutput output) throws Exception {
        runnerProperties.setStartDate("2026-04-01");

        WatchlistItem enabledItem = new WatchlistItem();
        enabledItem.setSymbol("2330");
        enabledItem.setName("台積電");
        enabledItem.setEnabled(true);

        WatchlistConfig config = new WatchlistConfig();
        config.setItems(List.of(enabledItem));

        AiAnalysisResult analysisResult = new AiAnalysisResult(
                "2330",
                "台積電",
                "原始分析內容",
                "台積電短線偏多，量價結構維持穩定。",
                "偏多",
                null
        );

        when(watchlistConfigLoader.load()).thenReturn(config);
        when(stockAnalysisFacade.analyzeEnabledStocks("2026-04-01")).thenReturn(List.of(analysisResult));

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(stockAnalysisFacade).analyzeEnabledStocks("2026-04-01");
        verify(analysisReportWriter, never()).write(List.of(analysisResult));
        assertThat(output).contains("2330 台積電");
        assertThat(output).contains("偏多");
        assertThat(output).contains("僅輸出 log，不自動發送 LINE");
    }

    @Test
    void run_shouldWriteReportWhenReportOutputEnabled(CapturedOutput output) throws Exception {
        runnerProperties.setStartDate("2026-04-01");
        reportProperties.setEnabled(true);

        WatchlistItem enabledItem = new WatchlistItem();
        enabledItem.setSymbol("2330");
        enabledItem.setName("台積電");
        enabledItem.setEnabled(true);

        WatchlistConfig config = new WatchlistConfig();
        config.setItems(List.of(enabledItem));

        AiAnalysisResult analysisResult = new AiAnalysisResult(
                "2330",
                "台積電",
                "原始分析內容",
                "台積電短線偏多，量價結構維持穩定。",
                "偏多",
                null
        );

        when(watchlistConfigLoader.load()).thenReturn(config);
        when(stockAnalysisFacade.analyzeEnabledStocks("2026-04-01")).thenReturn(List.of(analysisResult));
        when(analysisReportWriter.write(List.of(analysisResult)))
                .thenReturn(Optional.of(Path.of("target/stock-analysis-reports/stock-analysis-report-20260407-141230.json")));

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(analysisReportWriter).write(List.of(analysisResult));
        assertThat(output).contains("已輸出 JSON 報告");
    }
}
