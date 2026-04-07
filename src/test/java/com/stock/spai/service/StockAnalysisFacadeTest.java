package com.stock.spai.service;

import com.stock.spai.config.WatchlistConfigLoader;
import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.FinMindResponse;
import com.stock.spai.dto.WatchlistConfig;
import com.stock.spai.dto.WatchlistItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class StockAnalysisFacadeTest {

    @Mock
    private WatchlistConfigLoader watchlistConfigLoader;

    @Mock
    private FinMindService finMindService;

    @Mock
    private StockAiAnalyzer stockAiAnalyzer;

    @Mock
    private FinMindPromptSummaryBuilder finMindPromptSummaryBuilder;

    @Mock
    private AiAnalysisResultBuilder aiAnalysisResultBuilder;

    @InjectMocks
    private StockAnalysisFacade stockAnalysisFacade;

    @Test
    void analyzeEnabledStocks_shouldReturnResultsForEnabledItemsOnly() {
        WatchlistItem enabledItem = new WatchlistItem();
        enabledItem.setSymbol("2330");
        enabledItem.setName("台積電");
        enabledItem.setEnabled(true);

        WatchlistItem disabledItem = new WatchlistItem();
        disabledItem.setSymbol("2317");
        disabledItem.setName("鴻海");
        disabledItem.setEnabled(false);

        WatchlistConfig config = new WatchlistConfig();
        config.setItems(List.of(enabledItem, disabledItem));

        when(watchlistConfigLoader.load()).thenReturn(config);
        when(finMindService.getStockPrice("2330", "2026-04-01")).thenReturn(Mono.just(createResponse()));
        when(finMindService.getInstitutionalInvestors("2330", "2026-04-01")).thenReturn(Mono.just(createResponse()));
        when(finMindService.getMarginData("2330", "2026-04-01")).thenReturn(Mono.just(createResponse()));
        when(finMindPromptSummaryBuilder.buildSummary(
                eq("2330"),
                eq("台積電"),
                eq("2026-04-01"),
                any(FinMindResponse.class),
                any(FinMindResponse.class),
                any(FinMindResponse.class)
        )).thenReturn("台積電摘要");
        when(stockAiAnalyzer.analyzeSummary("2330", "台積電摘要")).thenReturn("台積電分析結果，偏多看待。");
        when(aiAnalysisResultBuilder.build(eq("2330"), eq("台積電"), eq("台積電分析結果，偏多看待。"), any()))
                .thenAnswer(invocation -> new AiAnalysisResult(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        "台積電分析結果",
                        "偏多",
                        invocation.getArgument(3)
                ));

        List<AiAnalysisResult> results = stockAnalysisFacade.analyzeEnabledStocks("2026-04-01");

        assertEquals(1, results.size());
        assertEquals("2330", results.getFirst().getSymbol());
        assertEquals("台積電", results.getFirst().getName());
        assertEquals("台積電分析結果，偏多看待。", results.getFirst().getRawText());
        assertEquals("台積電分析結果", results.getFirst().getSummary());
        assertEquals("偏多", results.getFirst().getStance());
        assertNotNull(results.getFirst().getContext());
        assertEquals("2026-04-01", results.getFirst().getContext().getStartDate());
        assertEquals("[]", results.getFirst().getContext().getPriceDataText());
        assertEquals("台積電摘要", results.getFirst().getContext().getPromptSummary());

        verify(finMindService).getStockPrice("2330", "2026-04-01");
        verify(finMindService).getInstitutionalInvestors("2330", "2026-04-01");
        verify(finMindService).getMarginData("2330", "2026-04-01");
        verify(finMindService, never()).getStockPrice("2317", "2026-04-01");
        verify(stockAiAnalyzer).analyzeSummary("2330", "台積電摘要");
        verify(aiAnalysisResultBuilder).build(eq("2330"), eq("台積電"), eq("台積電分析結果，偏多看待。"), any());
        verify(stockAiAnalyzer, never()).analyzeSummary("2317", "台積電摘要");
    }

    private FinMindResponse createResponse() {
        FinMindResponse response = new FinMindResponse();
        response.setData(List.of());
        return response;
    }
}
