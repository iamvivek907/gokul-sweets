package com.gokulsweets.restaurant.order.controller;
import com.gokulsweets.restaurant.order.service.KitchenPlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/orders/planning")
public class KitchenPlanningController {
 private final KitchenPlanningService planning;
 @GetMapping public KitchenPlanningService.Plan get(@RequestParam long branchId,
  @RequestParam(defaultValue="ALL") KitchenPlanningService.Filter filter,
  @RequestParam(required=false) LocalDate date,@RequestParam(required=false) LocalTime start,
  @RequestParam(defaultValue="0") int page){return planning.get(branchId,filter,date,start,page);}
}
