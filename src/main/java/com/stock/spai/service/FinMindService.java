package com.stock.spai.service;

import com.stock.spai.dto.FinMindResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
public class FinMindService {

    private final WebClient webClient;

    @Value("${finmind.api.token}") // 從 application.properties 或 Secrets 讀取
    private String apiToken;

    public FinMindService(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("https://api.finmindtrade.com/api/v4").build();
    }

    // 1. 抓取價格數據 (dataset: TaiwanStockPrice)
    public Mono<FinMindResponse> getStockPrice(String stockId, String startDate) {
        return fetchFromFinMind("TaiwanStockPrice", stockId, startDate);
    }

    // 2. 抓取法人數據 (dataset: TaiwanStockInstitutionalInvestorsBuySell)
    public Mono<FinMindResponse> getInstitutionalInvestors(String stockId, String startDate) {
        return fetchFromFinMind("TaiwanStockInstitutionalInvestorsBuySell", stockId, startDate);
    }

    // 封裝重複的 WebClient 邏輯
    private Mono<FinMindResponse> fetchFromFinMind(String dataset, String stockId, String startDate) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/data")
                        .queryParam("dataset", dataset)
                        .queryParam("data_id", stockId)
                        .queryParam("start_date", startDate)
                        .queryParam("token", apiToken)
                        .build())
                .retrieve()
                .bodyToMono(FinMindResponse.class);
    }

    // 抓取融資融券 (dataset: TaiwanStockMarginPurchaseShortSale)
    public Mono<FinMindResponse> getMarginData(String stockId, String startDate) {
        return fetchFromFinMind("TaiwanStockMarginPurchaseShortSale", stockId, startDate);
    }

    /**
     * 便捷方法：預設抓取過去 7 天的資料
     */
    public Mono<FinMindResponse> getStockPrice(String stockId) {
        String defaultStartDate = LocalDate.now().minusDays(7).toString();
        return this.getStockPrice(stockId, defaultStartDate);
    }
}