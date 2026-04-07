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

    private final ChatClient chatClient;

    @Value("classpath:/prompts/stock-analysis.st")
    private Resource analysisTemplate;

    // 邊帶邊學：將方法參數增加到 4 個，對應測試程式的呼叫
    public String analyze(String stockId, String priceData, String institutionalData, String marginData) {

        // 1. 手動創建一個模板渲染器
        PromptTemplate template = new PromptTemplate(analysisTemplate);

        // 2. 渲染成最終要發送的字串
        // 這種寫法能讓 IDE 完全看懂變數對應，紅字會消失
        String renderedPrompt = template.render(Map.of(
                "user", "投資人",
                "stockId", stockId,
                "date", LocalDate.now().toString(),
                "priceHistory", priceData,
                "institutionalData", institutionalData,
                "marginData", marginData // 修正：這裡改用傳入的變數，不再寫死為"尚無數據"
        ));

        // 3. 直接將字串餵給 chatClient
        return chatClient.prompt()
                .user(renderedPrompt)
                .call()
                .content();
    }

    /**
     * 低侵入提供綜合摘要入口，沿用既有 prompt 與分析流程。
     */
    public String analyzeSummary(String stockId, String promptSummary) {
        return analyze(
                stockId,
                "綜合摘要如下：\n" + promptSummary,
                "已整合於綜合摘要中，請優先參考綜合摘要。",
                "已整合於綜合摘要中，請優先參考綜合摘要。"
        );
    }
}
