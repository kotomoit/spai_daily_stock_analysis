package com.stock.spai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.spai.config.StockAnalysisReportProperties;
import com.stock.spai.dto.AiAnalysisResult;
import com.stock.spai.dto.AnalysisReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 將分析結果輸出為 JSON 報告檔案。
 */
@Component
public class AnalysisReportWriter {

    private static final Logger log = LoggerFactory.getLogger(AnalysisReportWriter.class);
    private static final String DEFAULT_OUTPUT_DIR = "target/stock-analysis-reports";
    private static final String FILE_NAME_PREFIX = "stock-analysis-report-";
    private static final DateTimeFormatter FILE_NAME_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ObjectMapper objectMapper;
    private final StockAnalysisReportProperties reportProperties;
    private final Clock clock;

    @Autowired
    public AnalysisReportWriter(ObjectMapper objectMapper, StockAnalysisReportProperties reportProperties) {
        this(objectMapper, reportProperties, Clock.systemDefaultZone());
    }

    AnalysisReportWriter(ObjectMapper objectMapper,
                         StockAnalysisReportProperties reportProperties,
                         Clock clock) {
        this.objectMapper = objectMapper;
        this.reportProperties = reportProperties;
        this.clock = clock;
    }

    public Optional<Path> write(List<AiAnalysisResult> results) {
        List<AiAnalysisResult> safeResults = results == null ? List.of() : results.stream()
                .filter(Objects::nonNull)
                .toList();

        if (safeResults.isEmpty()) {
            log.warn("本次沒有可輸出的分析結果，略過 JSON 報告寫入。");
            return Optional.empty();
        }

        Path outputDir = resolveOutputDir();
        ZonedDateTime generatedAt = ZonedDateTime.now(clock);
        AnalysisReport report = AnalysisReport.from(safeResults, generatedAt);

        try {
            Files.createDirectories(outputDir);
            Path outputPath = resolveOutputPath(outputDir, generatedAt);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), report);
            Path normalizedPath = outputPath.toAbsolutePath().normalize();
            log.info("分析報告已輸出至 {}", normalizedPath);
            return Optional.of(normalizedPath);
        } catch (IOException exception) {
            throw new IllegalStateException("輸出分析報告失敗: " + outputDir.toAbsolutePath().normalize(), exception);
        }
    }

    private Path resolveOutputDir() {
        if (!StringUtils.hasText(reportProperties.getOutputDir())) {
            return Paths.get(DEFAULT_OUTPUT_DIR).toAbsolutePath().normalize();
        }
        return Paths.get(reportProperties.getOutputDir()).toAbsolutePath().normalize();
    }

    private Path resolveOutputPath(Path outputDir, ZonedDateTime generatedAt) throws IOException {
        String baseFileName = FILE_NAME_PREFIX + generatedAt.toLocalDateTime().format(FILE_NAME_TIME_FORMATTER);
        Path candidate = outputDir.resolve(baseFileName + ".json");
        int sequence = 1;

        while (Files.exists(candidate)) {
            candidate = outputDir.resolve(baseFileName + "-" + String.format("%02d", sequence) + ".json");
            sequence++;
        }

        return candidate;
    }
}
