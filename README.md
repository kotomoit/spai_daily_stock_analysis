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

若 PowerShell 當前主控台不是 UTF-8，讀取 `README`、workflow 或其他 UTF-8 檔案時可能出現亂碼，建議同一個對話先補上：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
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

若要在 GitHub Actions 使用，請先建立以下 4 個 repository secrets：

- `GOOGLE_API_KEY`
- `FINMIND_API_TOKEN`
- `LINE_MESSAGING_TOKEN`
- `LINE_MESSAGING_USER_ID`

目前 workflow 的實際做法是：

- `GOOGLE_API_KEY`、`FINMIND_API_TOKEN`：可放在 job-level `env`，供分析主流程使用
- `LINE_MESSAGING_TOKEN`、`LINE_MESSAGING_USER_ID`：只在真正需要發 LINE 的 step 才注入，避免在不發 LINE 的情境提早暴露

```yaml
jobs:
  daily-analysis:
    env:
      GOOGLE_API_KEY: ${{ secrets.GOOGLE_API_KEY }}
      FINMIND_API_TOKEN: ${{ secrets.FINMIND_API_TOKEN }}

    steps:
      - name: 需要發送 LINE 時才注入 LINE secrets
        if: steps.execution-plan.outputs.line_enabled == 'true'
        env:
          LINE_MESSAGING_TOKEN: ${{ secrets.LINE_MESSAGING_TOKEN }}
          LINE_MESSAGING_USER_ID: ${{ secrets.LINE_MESSAGING_USER_ID }}
        run: |
          echo "only inject LINE secrets when LINE is enabled"
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

目前正式 workflow 位於 [`.github/workflows/daily-analysis.yml`](.github/workflows/daily-analysis.yml)，支援：

- `workflow_dispatch`：手動觸發
- `schedule`：排程觸發

目前策略改為：

- `workflow_dispatch`：維持使用 `send_line` input 決定本次是否發送 LINE
- `schedule`：固定先跑正式分析與 artifact，是否發送 LINE 改由 `Repository Variable` `SCHEDULED_LINE_ENABLED` 控制
- workflow 已加入 `concurrency`，避免手動觸發與排程重疊執行，降低重複分析或重複發 LINE 的風險

若 `SCHEDULED_LINE_ENABLED` 未設定或為 `false`，排程仍只會跑分析與 artifact，不會發送 LINE。

### Repository secrets 設定方式

請到 GitHub repository 的 `Settings > Secrets and variables > Actions`，建立以下 `Repository secrets`：

- `GOOGLE_API_KEY`
- `FINMIND_API_TOKEN`
- `LINE_MESSAGING_TOKEN`
- `LINE_MESSAGING_USER_ID`

### Repository variables 設定方式

請到 GitHub repository 的 `Settings > Secrets and variables > Actions`，切到 `Variables`，建立以下 `Repository variable`：

- `SCHEDULED_LINE_ENABLED`

建議值如下：

- `true`：排程執行時允許發送 LINE
- `false`：排程執行時不發送 LINE
- 未設定：等同關閉排程 LINE，適合先觀察排程分析與 artifact 是否穩定

這個 variable 屬於非敏感控制開關，可直接在 GitHub Actions 設定頁切換，不需要改 Java 程式碼，也不需要改 workflow 檔。

### 正式啟用排程 LINE 前最小檢查清單

建議在正式打開 `SCHEDULED_LINE_ENABLED=true` 前，至少先確認以下事項：

- `GOOGLE_API_KEY`、`FINMIND_API_TOKEN`、`LINE_MESSAGING_TOKEN`、`LINE_MESSAGING_USER_ID` 都已存在於 repository secrets
- 最近一次 `workflow_dispatch` 搭配 `send_line=true` 已能成功送出 LINE
- 最近一次 `workflow_dispatch` 搭配 `send_line=false` 或最近一次 `schedule` dry-run 已能成功產出 JSON report 與 artifact
- 已確認目前 `schedule` 為 GitHub Actions 的 UTC cron `30 7 * * 1-5`，換算台北時間約為平日 `15:30`
- 已確認要接收通知的 LINE 帳號、token 與實際接收情境都正確，避免一啟用就送到錯誤對象

### 正式啟用排程 LINE 建議節奏

建議啟用節奏如下：

1. 先確認 `Repository Variable` `SCHEDULED_LINE_ENABLED` 目前為 `false`，或暫時未設定
2. 先手動執行一次 `workflow_dispatch` 並設 `send_line=false`，確認分析主線、summary 與 artifact 都正常
3. 再手動執行一次 `workflow_dispatch` 並設 `send_line=true`，確認 LINE 實際可送達且內容合理
4. 確認前兩步都穩定後，再把 `SCHEDULED_LINE_ENABLED` 改成 `true`
5. 觀察前幾個交易日的 `run summary`、artifact 與 LINE 實際送達狀況
6. 若有異常，立即把 `SCHEDULED_LINE_ENABLED` 改回 `false` 或直接刪除

這個節奏的重點是先用手動路徑驗證「能不能送」與「送了長什麼樣」，再把相同主流程交給排程自動化。

### 手動驗證路徑與排程啟用途徑差異

兩條路徑共用同一套分析、報告輸出與 LINE 發送主流程，差異只在觸發來源與 LINE 開關判定來源：

| 項目 | 手動驗證路徑 | 排程啟用途徑 |
| --- | --- | --- |
| 觸發方式 | `workflow_dispatch` | `schedule` |
| LINE 開關來源 | `send_line` input | `Repository Variable` `SCHEDULED_LINE_ENABLED` |
| 建議用途 | 驗證分析、artifact、LINE 內容與收件設定 | 正式自動執行與日常通知 |
| 建議啟用時機 | 啟用前與啟用後都可當人工驗證入口 | 確認手動路徑穩定後再打開 |
| 快速停用方式 | 下次手動改成 `send_line=false` 即可 | 把 `SCHEDULED_LINE_ENABLED` 改回 `false` 或刪除 |

因此這一輪正式啟用排程 LINE，並不需要改 Java 流程；真正要管控的是哪一條觸發路徑有權限把既有 LINE 發送開關打開。

### 手動觸發方式

1. 進入 GitHub repository 的 `Actions`
2. 點選 `Daily Stock Analysis`
3. 按右上角 `Run workflow`
4. 視需要把 `send_line` 選成 `true` 或 `false`
5. 選擇分支後送出

若本次只想驗證分析與 artifact，請保持 `send_line=false`。

若要手動驗證 LINE 通知，才切換成 `send_line=true`。這條路徑會保留，作為排程開啟前後都可用的人工驗證入口。

### Schedule dry-run 驗證方式

若要先驗證排程主流程、但暫時不讓排程自動發 LINE，建議照以下方式進行：

1. 確認 `SCHEDULED_LINE_ENABLED` 未設定，或明確設為 `false`
2. 等待下一次 `schedule` 觸發
3. 到該次 Actions run 檢查 summary 與 log

預期結果：

- 觸發來源會顯示為 `schedule`
- `LINE 判定說明` 會指出 `SCHEDULED_LINE_ENABLED` 未設定或為 `false`
- JSON report 仍會產出並上傳成 artifact

若想在排程時間到來前先做快速預檢，可先用 `workflow_dispatch` 搭配 `send_line=false` 驗證分析與 artifact 主線；真正的 `schedule` 判定結果，則以下一次排程 run 的 summary 為準。

### 正式啟用後建議觀察

正式把 `SCHEDULED_LINE_ENABLED` 打開後，建議至少連續觀察前幾個交易日的以下結果：

- `執行前摘要` 是否顯示 `觸發來源：schedule`
- `執行前摘要` 與 `執行結果` 是否都顯示 `本次 LINE 發送：true`
- `LINE 判定說明` 是否明確指出 `schedule` 且 `SCHEDULED_LINE_ENABLED=true`
- JSON report 是否有成功產出，artifact 名稱是否正常
- LINE 是否在預期時段送達，且內容與同次 run 的 artifact 可合理對照
- 是否出現重複通知、未送達、artifact 成功但 LINE 失敗，或 LINE 成功但內容異常等情況

若只是想確認排程是否真的走到「可發 LINE」分支，優先看 `run summary` 即可；若要追查內容或失敗原因，再回頭比對 log 與下載 artifact。

### 快速回退停用排程 LINE

若排程 LINE 已開啟，但要快速停用，不需要修改 Java 程式碼，也不需要修改 workflow：

1. 到 `Settings > Secrets and variables > Actions > Variables`
2. 將 `SCHEDULED_LINE_ENABLED` 改成 `false`，或直接刪除
3. 下一次 `schedule` 觸發時，就會自動回到只跑分析與 artifact、不發 LINE 的模式

若需要更強的保守處置，例如暫時連排程分析都不想跑，才再到 GitHub Actions 頁面停用整個 workflow。

### Artifact 下載位置

workflow 成功後，可在該次 run 的摘要頁面下方 `Artifacts` 區塊下載 JSON 報告壓縮檔；artifact 名稱會是：

```text
stock-analysis-report-<run_number>
```

workflow 會先在 `mvn test` 階段明確關閉 `runner/report`，再於後續 `spring-boot:run` 步驟單獨開啟正式分析，避免測試 lifecycle 誤打真實外部 API。

目前 workflow summary 會額外標示：

- 本次觸發來源是 `workflow_dispatch` 還是 `schedule`
- 本次 LINE 是否啟用
- LINE 啟用或未啟用的判定原因
- JSON report 是否成功產出
- artifact 名稱與範例檔案

因此若要確認排程 LINE 為何沒有發送，或確認本次是否只是 dry-run，可直接從 run summary 判讀，不需要回頭看 Java 程式碼。

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
