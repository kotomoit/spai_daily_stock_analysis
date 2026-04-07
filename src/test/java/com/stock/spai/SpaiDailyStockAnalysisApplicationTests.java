package com.stock.spai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 僅驗證 Spring context 可正常啟動，並在測試時明確關閉正式 runner 與報表輸出，
 * 避免 workflow 或外部環境變數誤開啟後打到真實外部 API。
 */
@SpringBootTest(properties = {
        "stock.analysis.runner.enabled=false",
        "stock.analysis.report.enabled=false"
})
@Tag("mock-integration")
class SpaiDailyStockAnalysisApplicationTests {

    @Test
    void contextLoads() {
    }

}
