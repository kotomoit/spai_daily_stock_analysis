package com.stock.spai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.StockAnalysisContext;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("mock-integration")
class LineMessagingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockWebServer mockWebServer;
    private LineMessagingService lineMessagingService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        lineMessagingService = new LineMessagingService(webClient, new LineFlexMessageBuilder());
        ReflectionTestUtils.setField(lineMessagingService, "channelAccessToken", "test-token");
        ReflectionTestUtils.setField(lineMessagingService, "myUserId", "U123456");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    void sendProfessionalFlex_shouldSendBuilderPayloadToLineApi() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}"));

        StockAnalysisContext context = new StockAnalysisContext();
        context.setSymbol("2330");
        context.setName("台積電");
        context.setLatestDataDate("2026-04-07");
        context.setLatestPriceSummary("2026-04-07 收盤 912.00");
        context.setVolumeSummary("2026-04-07 成交量 32,456,789");

        AiAnalysisResult analysisResult = new AiAnalysisResult(
                "2330",
                "台積電",
                "短線量能回溫，整體偏多，但須留意高檔震盪。",
                "短線量能回溫，整體偏多，但須留意高檔震盪。",
                "偏多",
                context
        );

        StepVerifier.create(lineMessagingService.sendProfessionalFlex(analysisResult))
                .expectNext("{\"status\":\"ok\"}")
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("Bearer test-token", request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Content-Type"));

        Map<String, Object> payload = objectMapper.readValue(
                request.getBody().inputStream(),
                new TypeReference<>() {
                }
        );

        assertEquals("U123456", payload.get("to"));

        Map<String, Object> message = getFirstMessage(payload);
        assertEquals("flex", message.get("type"));
        assertTrue(((String) message.get("altText")).contains("2330"));

        Map<String, Object> contents = getMap(message, "contents");
        Map<String, Object> body = getMap(contents, "body");
        List<?> bodyContents = getList(body, "contents");

        Map<String, Object> priceRow = castMap(bodyContents.get(0));
        List<?> priceContents = getList(priceRow, "contents");
        assertEquals("價格摘要", castMap(priceContents.get(0)).get("text"));
        assertEquals("2026-04-07 收盤 912.00", castMap(priceContents.get(1)).get("text"));

        Map<String, Object> summaryBox = castMap(bodyContents.get(4));
        List<?> summaryContents = getList(summaryBox, "contents");
        assertEquals("短線量能回溫，整體偏多，但須留意高檔震盪。", castMap(summaryContents.get(1)).get("text"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFirstMessage(Map<String, Object> payload) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        return messages.getFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private List<?> getList(Map<String, Object> source, String key) {
        return (List<?>) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
