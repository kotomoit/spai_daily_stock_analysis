package com.stock.spai.dto;

import lombok.Data;
import java.util.List;

@Data
public class FinMindResponse {
    private int status;
    private String msg;
    private List<StockData> data;

    @Data
    public static class StockData {
        private String date;
        @com.fasterxml.jackson.annotation.JsonProperty("stock_id")
        private String stockId;
        @com.fasterxml.jackson.annotation.JsonProperty("stock_name")
        private String stockName;
        @com.fasterxml.jackson.annotation.JsonProperty("industry_category")
        private String industryCategory;

        // 價格相關
        private double open;
        private double max;
        private double min;
        private double close;
        @com.fasterxml.jackson.annotation.JsonProperty("Trading_Volume")
        private long vol;

        // 法人相關：FinMind 的欄位名稱其實是 "name"
        @com.fasterxml.jackson.annotation.JsonProperty("name")
        private String type;
        private long buy;
        private long sell;

        // 融資融券相關
        @com.fasterxml.jackson.annotation.JsonProperty("MarginPurchaseBuy")
        private long marginBuy;
        @com.fasterxml.jackson.annotation.JsonProperty("MarginPurchaseTotal")
        private long marginTotal;
    }
}
