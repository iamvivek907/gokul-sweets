package com.gokulsweets.restaurant.order.controller;
import com.gokulsweets.restaurant.order.service.OrderDemandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/orders/planning")
public class OrderDemandController {
    private final OrderDemandService demand;
    @GetMapping("/demand") public List<OrderDemandService.Demand> get(@RequestParam long branchId,@RequestParam LocalDate from,@RequestParam LocalDate to){return demand.get(branchId,from,to);}
    @GetMapping("/demand/export") public ResponseEntity<byte[]> export(@RequestParam long branchId,@RequestParam LocalDate from,@RequestParam LocalDate to){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"gokul-demand-"+branchId+"-"+from+"-"+to+".xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(demand.export(branchId,from,to));
    }
    @GetMapping("/products") public List<OrderDemandService.Policy> policies(@RequestParam long branchId){return demand.policies(branchId);}
    public record PolicyRequest(boolean earlyPreparationAllowed) {}
    @PutMapping("/products/{productId}") public ResponseEntity<Void> policy(@PathVariable long productId,@RequestParam long branchId,@RequestBody PolicyRequest input){demand.policy(branchId,productId,input.earlyPreparationAllowed());return ResponseEntity.noContent().build();}
}
