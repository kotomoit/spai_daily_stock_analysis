package com.stock.spai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 先以低侵入方式封裝 AI 分析結果，保留原始文字以利後續逐步擴充。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResult {

    private String symbol;
    private String name;
    private String rawText;
    private String summary;
    private String stance;
    private StockAnalysisContext context;
}
