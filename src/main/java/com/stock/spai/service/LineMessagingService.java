package com.stock.spai.service;

import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.StockAnalysisContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class LineMessagingService {

    private final WebClient webClient;
    private final LineFlexMessageBuilder lineFlexMessageBuilder;

    @Value("${line.messaging.token}")
    private String channelAccessToken;

    @Value("${line.messaging.user-id}")
    private String myUserId;

    @Autowired
    public LineMessagingService(WebClient.Builder builder, LineFlexMessageBuilder lineFlexMessageBuilder) {
        this(builder.baseUrl("https://api.line.me/v2/bot/message/push").build(), lineFlexMessageBuilder);
    }

    LineMessagingService(WebClient webClient, LineFlexMessageBuilder lineFlexMessageBuilder) {
        this.webClient = webClient;
        this.lineFlexMessageBuilder = lineFlexMessageBuilder;
    }

    public Mono<String> sendPushMessage(String text) {
        Map<String, Object> body = Map.of(
                "to", myUserId,
                "messages", List.of(
                        Map.of("type", "text", "text", text)
                )
        );

        return sendPayload(body);
    }

    public Mono<String> sendFlexNotification(String stockId, String aiAnalysis) {
        Map<String, Object> flexContent = Map.of(
                "type", "bubble",
                "header", Map.of(
                        "type", "box",
                        "layout", "vertical",
                        "contents", List.of(
                                Map.of(
                                        "type", "text",
                                        "text", "📈 " + stockId + " AI 分析",
                                        "weight", "bold",
                                        "size", "xl",
                                        "color", "#ffffff"
                                )
                        ),
                        "backgroundColor", "#0367D9"
                ),
                "body", Map.of(
                        "type", "box",
                        "layout", "vertical",
                        "contents", List.of(
                                Map.of(
                                        "type", "text",
                                        "text", aiAnalysis,
                                        "wrap", true,
                                        "size", "sm",
                                        "color", "#333333"
                                )
                        )
                )
        );

        Map<String, Object> payload = Map.of(
                "to", myUserId,
                "messages", List.of(
                        Map.of(
                                "type", "flex",
                                "altText", stockId + " AI 分析通知",
                                "contents", flexContent
                        )
                )
        );

        return sendPayload(payload);
    }

    /**
     * 專業版 Flex 改為直接使用分析結果資料組裝 payload。
     */
    public Mono<String> sendProfessionalFlex(AiAnalysisResult analysisResult) {
        Map<String, Object> payload = lineFlexMessageBuilder.buildProfessionalFlexPayload(myUserId, analysisResult);
        return sendPayload(payload);
    }

    /**
     * 保留既有呼叫介面，避免影響舊程式碼；若未提供真實價量資料，則使用保守摘要。
     */
    public Mono<String> sendProfessionalFlex(String stockId, String aiAnalysis, String priceJson) {
        StockAnalysisContext context = new StockAnalysisContext();
        context.setSymbol(stockId);
        context.setLatestPriceSummary("價格摘要待補");
        context.setVolumeSummary("量能摘要待補");
        context.setLatestClosePrice("價格摘要待補");
        context.setPriceChange("待補");
        context.setPriceChangePercent("待補");
        context.setLatestVolume("量能摘要待補");
        context.setForeignInvestorSummary("法人摘要待補");
        context.setMarginSummary("融資摘要待補");

        AiAnalysisResult analysisResult = new AiAnalysisResult(
                stockId,
                "",
                aiAnalysis,
                aiAnalysis,
                "",
                context
        );

        return sendProfessionalFlex(analysisResult);
    }

    private Mono<String> sendPayload(Map<String, Object> payload) {
        return webClient.post()
                .header("Authorization", "Bearer " + channelAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class);
    }
}
