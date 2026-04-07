package com.stock.spai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 集中管理手動分析 runner 的執行設定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "stock.analysis.runner")
public class StockAnalysisRunnerProperties {

    /**
     * 是否在應用啟動時自動執行股票分析流程。
     */
    private boolean enabled = false;

    /**
     * 手動指定分析起始日，未設定時沿用 facade 預設邏輯。
     */
    private String startDate;
}
