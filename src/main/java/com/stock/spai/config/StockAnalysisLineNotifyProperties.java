package com.stock.spai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LINE 通知發送設定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "stock.analysis.line-notify")
public class StockAnalysisLineNotifyProperties {

    /**
     * 是否在正式分析完成後發送 LINE 通知。
     */
    private boolean enabled = false;
}
