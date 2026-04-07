package com.stock.spai;

import com.stock.spai.service.FinMindService;
import com.stock.spai.service.LineMessagingService;
import com.stock.spai.service.StockAiAnalyzer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;

import java.time.LocalDate; // Java 8 以後處理日期最推薦的工具

@SpringBootTest
@Tag("manual-integration")
@EnabledIfSystemProperty(named = "manual.integration", matches = "true")
class StockAnalysisIntegrationTest {

    @Autowired
    private FinMindService finMindService;

    @Autowired
    private StockAiAnalyzer aiAnalyzer;

    @Autowired
    private LineMessagingService lineMessagingService;

    @Test
    void testUltraWorkflow() {
        String startDate = LocalDate.now().minusDays(10).toString();
        String stockId = "2330";

        // 同時併發抓取三種資料，效率最高
        var finalResult = Mono.zip(
                finMindService.getStockPrice(stockId, startDate),
                finMindService.getInstitutionalInvestors(stockId, startDate),
                finMindService.getMarginData(stockId, startDate)
        ).flatMap(tuple -> {
            // tuple.getT1() 是價格, T2 是法人, T3 是融資
            String result = aiAnalyzer.analyze(
                    stockId,
                    tuple.getT1().getData().toString(),
                    tuple.getT2().getData().toString(),
                    tuple.getT3().getData().toString() // 傳入融資數據
            );
            return Mono.just(result);
        }).block();

        System.out.println("====== 終極版 AI 分析報告 ======");
        System.out.println(finalResult);
    }

}
