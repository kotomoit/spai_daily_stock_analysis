package com.stock.spai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.spai.config.StockAnalysisReportProperties;
import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.StockAnalysisContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class AnalysisReportWriterTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void write_shouldCreateTimestampedJsonReportFile() throws Exception {
        StockAnalysisReportProperties reportProperties = new StockAnalysisReportProperties();
        reportProperties.setOutputDir(tempDir.toString());

        AnalysisReportWriter writer = new AnalysisReportWriter(
                objectMapper,
                reportProperties,
                Clock.fixed(Instant.parse("2026-04-07T06:12:30Z"), ZoneId.of("Asia/Taipei"))
        );

        AiAnalysisResult analysisResult = new AiAnalysisResult(
                "2330",
                "台積電",
                "原始分析內容",
                "台積電短線偏多，量價結構維持穩定。",
                "偏多",
                createContext()
        );

        Optional<Path> outputPath = writer.write(List.of(analysisResult));

        assertThat(outputPath).isPresent();
        assertThat(outputPath.orElseThrow().getFileName().toString())
                .isEqualTo("stock-analysis-report-20260407-141230.json");

        JsonNode reportRoot = objectMapper.readTree(Files.readString(outputPath.orElseThrow()));
        assertThat(reportRoot.path("reportDate").asText()).isEqualTo("2026-04-07");
        assertThat(reportRoot.path("resultCount").asInt()).isEqualTo(1);
        assertThat(reportRoot.path("items").get(0).path("symbol").asText()).isEqualTo("2330");
        assertThat(reportRoot.path("items").get(0).path("summary").asText())
                .isEqualTo("台積電短線偏多，量價結構維持穩定。");
        assertThat(reportRoot.path("items").get(0).path("context").path("industryCategory").asText())
                .isEqualTo("半導體業");
        assertThat(reportRoot.path("items").get(0).path("context").path("latestDataDate").asText())
                .isEqualTo("2026-04-07");
        assertThat(reportRoot.path("items").get(0).path("context").path("priceChange").asText())
                .isEqualTo("+12.00");
    }

    @Test
    void write_shouldSkipWhenNoAnalysisResult() throws Exception {
        StockAnalysisReportProperties reportProperties = new StockAnalysisReportProperties();
        reportProperties.setOutputDir(tempDir.toString());

        AnalysisReportWriter writer = new AnalysisReportWriter(
                objectMapper,
                reportProperties,
                Clock.fixed(Instant.parse("2026-04-07T06:12:30Z"), ZoneId.of("Asia/Taipei"))
        );

        Optional<Path> outputPath = writer.write(List.of());

        assertThat(outputPath).isEmpty();
        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files.count()).isZero();
        }
    }

    private StockAnalysisContext createContext() {
        StockAnalysisContext context = new StockAnalysisContext();
        context.setSymbol("2330");
        context.setName("台積電");
        context.setIndustryCategory("半導體業");
        context.setStartDate("2026-04-01");
        context.setPriceDataText("[price-data]");
        context.setInstitutionalDataText("[institutional-data]");
        context.setMarginDataText("[margin-data]");
        context.setPromptSummary("摘要提示內容");
        context.setLatestPriceSummary("2026-04-07 收盤 912.00");
        context.setVolumeSummary("2026-04-07 成交量 32,456,789");
        context.setLatestDataDate("2026-04-07");
        context.setLatestClosePrice("912.00");
        context.setPriceChange("+12.00");
        context.setPriceChangePercent("+1.33%");
        context.setLatestVolume("32,456,789");
        context.setForeignInvestorSummary("買賣超 +3,200");
        context.setMarginSummary("買進 1,250，餘額 18,600，較前日 +450");
        return context;
    }
}
