package com.stock.spai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.spai.dto.WatchlistConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

@Component
public class WatchlistConfigLoader {

    static final String DEFAULT_RESOURCE_PATH = "watchlist.json";

    private final ObjectMapper objectMapper;
    private final String resourcePath;

    @Autowired
    public WatchlistConfigLoader(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_RESOURCE_PATH);
    }

    WatchlistConfigLoader(ObjectMapper objectMapper, String resourcePath) {
        this.objectMapper = objectMapper;
        this.resourcePath = resourcePath;
    }

    /**
     * 從 classpath 載入觀察清單設定，並轉成 DTO 供後續流程使用。
     */
    public WatchlistConfig load() {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new WatchlistConfigException("找不到觀察清單設定檔: " + resourcePath);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            WatchlistConfig config = objectMapper.readValue(inputStream, WatchlistConfig.class);

            // 若 JSON 未提供 items，這裡統一補成空清單，避免呼叫端額外判空。
            if (config.getItems() == null) {
                config.setItems(new ArrayList<>());
            }
            return config;
        } catch (JsonProcessingException exception) {
            throw new WatchlistConfigException("觀察清單設定檔格式錯誤: " + resourcePath, exception);
        } catch (IOException exception) {
            throw new WatchlistConfigException("讀取觀察清單設定檔失敗: " + resourcePath, exception);
        }
    }
}
