package com.stock.spai.service;

import com.stock.spai.dto.FinMindResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class FinMindPromptSummaryBuilderTest {

    private final FinMindPromptSummaryBuilder finMindPromptSummaryBuilder = new FinMindPromptSummaryBuilder();

    @Test
    void buildSummary_shouldReturnChineseSummary_whenResponsesContainData() {
        String summary = finMindPromptSummaryBuilder.buildSummary(
                "2330",
                "台積電",
                "半導體業",
                "2026-04-01",
                createPriceResponse(),
                createInstitutionalResponse(),
                createMarginResponse()
        );

        assertTrue(summary.contains("股票代號：2330"));
        assertTrue(summary.contains("股票名稱：台積電"));
        assertTrue(summary.contains("產業／板塊：半導體業"));
        assertTrue(summary.contains("價格資料重點："));
        assertTrue(summary.contains("最新開高低收：1000.00 / 1015.00 / 995.00 / 1008.00"));
        assertTrue(summary.contains("法人資料重點："));
        assertTrue(summary.contains("外資 買賣超 300"));
        assertTrue(summary.contains("投信 買賣超 -50"));
        assertTrue(summary.contains("融資資料重點："));
        assertTrue(summary.contains("區間融資餘額變化：150"));
    }

    private FinMindResponse createPriceResponse() {
        FinMindResponse response = new FinMindResponse();
        response.setData(List.of(
                createStockData("2026-04-01", 980, 1005, 975, 998, 120000, null, 0, 0, 0, 0),
                createStockData("2026-04-02", 1000, 1015, 995, 1008, 150000, null, 0, 0, 0, 0)
        ));
        return response;
    }

    private FinMindResponse createInstitutionalResponse() {
        FinMindResponse response = new FinMindResponse();
        response.setData(List.of(
                createStockData("2026-04-02", 0, 0, 0, 0, 0, "外資", 1200, 900, 0, 0),
                createStockData("2026-04-02", 0, 0, 0, 0, 0, "投信", 250, 300, 0, 0)
        ));
        return response;
    }

    private FinMindResponse createMarginResponse() {
        FinMindResponse response = new FinMindResponse();
        response.setData(List.of(
                createStockData("2026-04-01", 0, 0, 0, 0, 0, null, 0, 0, 500, 10000),
                createStockData("2026-04-02", 0, 0, 0, 0, 0, null, 0, 0, 650, 10150)
        ));
        return response;
    }

    private FinMindResponse.StockData createStockData(String date,
                                                      double open,
                                                      double max,
                                                      double min,
                                                      double close,
                                                      long volume,
                                                      String type,
                                                      long buy,
                                                      long sell,
                                                      long marginBuy,
                                                      long marginTotal) {
        FinMindResponse.StockData stockData = new FinMindResponse.StockData();
        stockData.setDate(date);
        stockData.setOpen(open);
        stockData.setMax(max);
        stockData.setMin(min);
        stockData.setClose(close);
        stockData.setVol(volume);
        stockData.setType(type);
        stockData.setBuy(buy);
        stockData.setSell(sell);
        stockData.setMarginBuy(marginBuy);
        stockData.setMarginTotal(marginTotal);
        return stockData;
    }
}
