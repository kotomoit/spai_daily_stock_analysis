package com.stock.spai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 股票分析流程中共用的上下文資料，
 * 供 AI 分析、LINE Flex 顯示與報告輸出共用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisContext {

    private String symbol;
    private String name;
    private String industryCategory;
    private String startDate;
    private String priceDataText;
    private String institutionalDataText;
    private String marginDataText;
    private String promptSummary;
    private String latestPriceSummary;
    private String volumeSummary;
    private String latestDataDate;
    private String latestClosePrice;
    private String priceChange;
    private String priceChangePercent;
    private String latestVolume;
    private String foreignInvestorSummary;
    private String marginSummary;
}
