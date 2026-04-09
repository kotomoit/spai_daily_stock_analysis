package com.stock.spai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate; // 務必匯入這個
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockAiAnalyzer {

    private static final String NO_EXTRA_SUMMARY = "目前未提供額外整合摘要，請改以原始資料區塊為主，並保持保守分析。";

    private final ChatClient chatClient;

    @Value("classpath:/prompts/stock-analysis.st")
    private Resource analysisTemplate;

    // 邊帶邊學：將方法參數增加到 4 個，對應測試程式的呼叫
    public String analyze(String stockId, String priceData, String institutionalData, String marginData) {
        return renderAndCall(
                stockId,
                NO_EXTRA_SUMMARY,
                priceData,
                institutionalData,
                marginData
        );
    }

    private String renderAndCall(String stockId,
                                 String marketDataSummary,
                                 String priceData,
                                 String institutionalData,
                                 String marginData) {
        PromptTemplate template = new PromptTemplate(analysisTemplate);
        String renderedPrompt = template.render(Map.of(
                "user", "投資人",
                "stockId", stockId,
                "date", LocalDate.now().toString(),
                "marketDataSummary", marketDataSummary,
                "priceHistory", priceData,
                "institutionalData", institutionalData,
                "marginData", marginData
        ));

        return chatClient.prompt()
                .user(renderedPrompt)
                .call()
                .content();
    }

    /**
     * 低侵入提供綜合摘要入口，沿用既有 prompt 與分析流程。
     */
    public String analyzeSummary(String stockId, String promptSummary) {
        return renderAndCall(
                stockId,
                promptSummary,
                "綜合摘要已整理，若需補充請僅參考此處提供的價格或技術線索。",
                "已整合於綜合摘要中，請優先參考綜合摘要。",
                "已整合於綜合摘要中，請優先參考綜合摘要。"
        );
    }
}
