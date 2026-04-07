package com.stock.spai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WatchlistConfig {

    /**
     * 儲存觀察清單項目，若設定檔未提供則以空清單處理。
     */
    private List<WatchlistItem> items = new ArrayList<>();
}
