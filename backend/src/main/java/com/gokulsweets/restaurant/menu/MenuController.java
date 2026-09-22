package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.menu.dto.MenuCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    public List<MenuCategoryResponse> getMenu(
            @RequestParam Long branchId
    ) {

        return menuService.getMenu(branchId);
    }
}