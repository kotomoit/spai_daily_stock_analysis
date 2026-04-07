package com.stock.spai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 封裝單一股票在分析流程中需要用到的原始資料與上下文資訊。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisContext {

    private String symbol;
    private String name;
    private String startDate;
    private String priceDataText;
    private String institutionalDataText;
    private String marginDataText;
    private String promptSummary;
    private String latestPriceSummary;
    private String volumeSummary;
    private String latestDataDate;
}
