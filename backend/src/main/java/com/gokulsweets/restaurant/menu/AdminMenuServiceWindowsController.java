package com.gokulsweets.restaurant.menu;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/branches/{branchId}/menu-service-windows") @RequiredArgsConstructor
public class AdminMenuServiceWindowsController {
 private final MenuServiceWindows windows;
 @GetMapping public MenuServiceWindows.Settings get(@PathVariable long branchId){return windows.settings(branchId);}
 @PutMapping public MenuServiceWindows.Settings save(@PathVariable long branchId,@RequestBody MenuServiceWindows.Settings settings){return windows.save(branchId,settings);}
}
