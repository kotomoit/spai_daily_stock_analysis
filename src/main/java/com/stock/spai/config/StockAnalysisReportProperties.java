package com.stock.spai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 分析報告輸出設定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "stock.analysis.report")
public class StockAnalysisReportProperties {

    /**
     * 是否啟用分析報告輸出。
     */
    private boolean enabled = false;

    /**
     * 分析報告輸出目錄。
     */
    private String outputDir = "target/stock-analysis-reports";
}
