package com.gokulsweets.restaurant.menu;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class MenuCacheHeadersTest {
    @Test
    void onlySuccessfulVersionedCatalogIsImmutableAndAvailabilityIsNeverPubliclyCached() {
        var catalog = mock(MenuCatalogService.class);
        var live = mock(MenuAvailabilityService.class);
        var menu = mock(MenuService.class);
        var controller = new MenuController(menu, catalog, live);
        when(catalog.get(1)).thenReturn(new MenuCatalogService.Catalog("version-a", List.of()));
        when(live.get(1))
                .thenReturn(
                        new MenuAvailabilityService.Availability("version-a", false, List.of()));
        assertThat(controller.versionedCatalog(1, "version-a").getHeaders().getCacheControl())
                .contains("public", "immutable");
        assertThat(controller.availability(1).getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(controller.getMenu(1L, "availability").getHeaders().getCacheControl())
                .isEqualTo("no-store");
        assertThatThrownBy(() -> controller.versionedCatalog(1, "outdated"))
                .hasMessageContaining("409");
    }
}
