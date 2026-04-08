# spai_daily_stock_analysis

## 環境變數設定

敏感資訊已改由環境變數提供，請不要把金鑰或憑證寫回主設定檔。

- `spring.ai.google.genai.api-key` -> `GOOGLE_API_KEY`
- `finmind.api.token` -> `FINMIND_API_TOKEN`
- `line.messaging.token` -> `LINE_MESSAGING_TOKEN`
- `line.messaging.user-id` -> `LINE_MESSAGING_USER_ID`

非敏感設定仍保留在 [`src/main/resources/application.properties`](src/main/resources/application.properties)，目前包含：

- Gemini model：`gemini-2.5-flash-lite`
- `stock.analysis.runner.enabled=false`
- `stock.analysis.report.enabled=false`
- `stock.analysis.line-notify.enabled=false`

## PowerShell 與 JDK 21

目前 PowerShell 環境若預設不是 JDK 21，請先切換後再跑 Maven：

```powershell
$env:JAVA_HOME = 'C:\OpenSDKs\jdk-21.0.2'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

再補上本機需要的敏感環境變數：

```powershell
$env:GOOGLE_API_KEY = '你的 Google API Key'
$env:FINMIND_API_TOKEN = '你的 FinMind Token'
$env:LINE_MESSAGING_TOKEN = '你的 LINE Messaging Token'
$env:LINE_MESSAGING_USER_ID = '你的 LINE User ID'
```

## 測試分層

執行預設測試：

```powershell
.\mvnw.cmd test
```

目前 `mvn test` 不依賴真實外部 API，維持既有測試分層：

- `unit`：純單元測試，以 Mockito / stub 為主
- `mock-integration`：Spring context 搭配 mock server 的整合測試
- `manual-integration`：需要真實 API 與環境變數的手動整合測試

若要手動執行真實外部 API 驗證，請使用：

```powershell
.\mvnw.cmd test -Pmanual-integration
```

## 手動分析 runner

目前 runner 仍維持第一版策略：執行分析流程、輸出 log、可選擇輸出 JSON 報告；LINE 發送改為由獨立開關控制，預設關閉。

可用設定：

- `stock.analysis.runner.enabled`
- `stock.analysis.runner.start-date`
- `stock.analysis.report.enabled`
- `stock.analysis.report.output-dir`
- `stock.analysis.line-notify.enabled`

若使用環境變數覆蓋，可對應為：

- `STOCK_ANALYSIS_RUNNER_ENABLED`
- `STOCK_ANALYSIS_RUNNER_START_DATE`
- `STOCK_ANALYSIS_REPORT_ENABLED`
- `STOCK_ANALYSIS_REPORT_OUTPUT_DIR`
- `STOCK_ANALYSIS_LINE_NOTIFY_ENABLED`

當 `stock.analysis.line-notify.enabled=true` 時，runner 會在分析完成並保留既有 log / report 流程後，直接逐筆沿用 `LineMessagingService` 與 `LineFlexMessageBuilder` 發送 LINE Flex 訊息。

若為 `false`，則只保留既有 log / JSON report 行為，不會呼叫 LINE 發送。

## JSON 報告輸出

當 `stock.analysis.report.enabled=true` 時，runner 會在分析完成後把結果輸出為 JSON 檔案。

預設輸出目錄：

```properties
stock.analysis.report.output-dir=target/stock-analysis-reports
```

檔名格式：

```text
stock-analysis-report-yyyyMMdd-HHmmss.json
```

若同一秒內重跑，writer 會自動補上流水號尾碼，避免覆蓋既有檔案。

## GitHub Pages / CI 建議

若未來要接 GitHub Pages 或其他靜態頁，可直接把 `stock.analysis.report.output-dir` 改為靜態站台會讀取的資料目錄，例如：

- `docs/data`
- `site/data`
- CI artifact 專用輸出目錄

這樣 runner 與 `AnalysisReportWriter` 不需要再改，只要讓前端頁面讀取同一份 JSON 即可。

## GitHub Actions secrets

若要在 GitHub Actions 使用，請將敏感資訊設為 repository secrets，再於 workflow 的 `env` 注入：

```yaml
env:
  GOOGLE_API_KEY: ${{ secrets.GOOGLE_API_KEY }}
  FINMIND_API_TOKEN: ${{ secrets.FINMIND_API_TOKEN }}
  LINE_MESSAGING_TOKEN: ${{ secrets.LINE_MESSAGING_TOKEN }}
  LINE_MESSAGING_USER_ID: ${{ secrets.LINE_MESSAGING_USER_ID }}
```

若 workflow 需要啟動 runner 與 JSON 報告輸出，可再加入：

```yaml
- name: 執行 Maven 測試
  env:
    STOCK_ANALYSIS_RUNNER_ENABLED: false
    STOCK_ANALYSIS_REPORT_ENABLED: false
    STOCK_ANALYSIS_LINE_NOTIFY_ENABLED: false
  run: ./mvnw -B test

- name: 執行正式分析 runner
  env:
    STOCK_ANALYSIS_RUNNER_ENABLED: true
    STOCK_ANALYSIS_REPORT_ENABLED: true
    STOCK_ANALYSIS_LINE_NOTIFY_ENABLED: false
  run: ./mvnw -B -DskipTests spring-boot:run
```

## GitHub Actions workflow

第一版 workflow 已放在 [`.github/workflows/daily-analysis.yml`](.github/workflows/daily-analysis.yml)，目前支援：

- `workflow_dispatch`：手動觸發
- `schedule`：排程觸發

其中 `schedule` 目前固定以 `STOCK_ANALYSIS_LINE_NOTIFY_ENABLED=false` 執行，避免第一版尚未完成完整驗證前就固定自動發送 LINE。

### Repository secrets 設定方式

請到 GitHub repository 的 `Settings > Secrets and variables > Actions`，建立以下 `Repository secrets`：

- `GOOGLE_API_KEY`
- `FINMIND_API_TOKEN`
- `LINE_MESSAGING_TOKEN`
- `LINE_MESSAGING_USER_ID`

### 手動觸發方式

1. 進入 GitHub repository 的 `Actions`
2. 點選 `Daily Stock Analysis`
3. 按右上角 `Run workflow`
4. 視需要把 `send_line` 選成 `true` 或 `false`
5. 選擇分支後送出

若本次只想驗證分析與 artifact，請保持 `send_line=false`。

若要手動驗證 LINE 通知，才切換成 `send_line=true`。

### Artifact 下載位置

workflow 成功後，可在該次 run 的摘要頁面下方 `Artifacts` 區塊下載 JSON 報告壓縮檔；artifact 名稱會是：

```text
stock-analysis-report-<run_number>
```

workflow 會先在 `mvn test` 階段明確關閉 `runner/report`，再於後續 `spring-boot:run` 步驟單獨開啟正式分析，避免測試 lifecycle 誤打真實外部 API。

### 第一版為何先採手動驗證 LINE

目前 LINE 發送先以手動觸發驗證為優先，原因是：

- 正式分析與 artifact 主線已先穩定，LINE 屬於後續外送行為，先用開關隔離風險較安全
- 手動觸發較容易比對單次分析結果、artifact 與 LINE 訊息內容是否一致
- 若一開始直接讓排程固定自動發送，當格式或收件設定仍在調整時，容易造成重複通知或錯誤通知

### 第一版為何先只做 artifact

目前先把 JSON report 保留在 CI artifact，原因是：

- 不需要額外引入 GitHub Pages deploy 流程
- 不需要處理靜態站台目錄結構與版本覆蓋策略
- 不需要自動 commit 回 repo，能先把排程分析與報告產出流程穩定下來

若未來要接 GitHub Pages，最小變更通常只需要：

- 把 `STOCK_ANALYSIS_REPORT_OUTPUT_DIR` 改到站台會讀取的資料目錄，例如 `docs/data`
- 在 workflow 後段補上靜態內容部署步驟
- 讓前端頁面讀取同一份 JSON report
