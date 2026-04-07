package com.stock.spai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.spai.dto.WatchlistConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class WatchlistConfigLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void load_shouldReturnWatchlistConfig_whenJsonIsValid() {
        WatchlistConfigLoader loader = new WatchlistConfigLoader(objectMapper, "watchlist-test.json");

        WatchlistConfig config = loader.load();

        assertEquals(2, config.getItems().size());
        assertEquals("2330", config.getItems().getFirst().getSymbol());
        assertEquals("台積電", config.getItems().getFirst().getName());
        assertTrue(config.getItems().getFirst().isEnabled());
        assertFalse(config.getItems().get(1).isEnabled());
    }

    @Test
    void load_shouldThrowException_whenResourceDoesNotExist() {
        WatchlistConfigLoader loader = new WatchlistConfigLoader(objectMapper, "watchlist-missing.json");

        WatchlistConfigException exception = assertThrows(WatchlistConfigException.class, loader::load);

        assertTrue(exception.getMessage().contains("找不到觀察清單設定檔"));
    }

    @Test
    void load_shouldThrowException_whenJsonFormatIsInvalid() {
        WatchlistConfigLoader loader = new WatchlistConfigLoader(objectMapper, "watchlist-invalid.json");

        WatchlistConfigException exception = assertThrows(WatchlistConfigException.class, loader::load);

        assertTrue(exception.getMessage().contains("觀察清單設定檔格式錯誤"));
    }

    @Test
    void load_shouldReturnEmptyItems_whenItemsFieldIsMissing() {
        WatchlistConfigLoader loader = new WatchlistConfigLoader(objectMapper, "watchlist-without-items.json");

        WatchlistConfig config = loader.load();

        assertNotNull(config);
        assertNotNull(config.getItems());
        assertTrue(config.getItems().isEmpty());
    }
}
