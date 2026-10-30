package com.gokulsweets.restaurant.meal;

import static com.gokulsweets.restaurant.meal.MealModels.*;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public meal discovery/quotes and staff-protected versioned meal configuration. */
@RestController
@RequiredArgsConstructor
public class MealController {
    private final MealService meals;

    /** Returns the selected branch published meal collection. */
    @GetMapping("/api/branches/{branchId}/meals")
    public List<Definition> catalog(@PathVariable long branchId) {
        long started = MethodTiming.start(MealController.class, "catalog(long)");
        try {
            return meals.catalog(branchId, false);
        } finally {
            MethodTiming.finish(started, MealController.class, "catalog(long)");
        }
    }

    /** Prices validated choices; does not reserve stock or promise pickup. */
    @PostMapping("/api/branches/{branchId}/meals/quote")
    public Quote quote(@PathVariable long branchId, @RequestBody Claim claim) {
        long started = MethodTiming.start(MealController.class, "quote(long,Claim)");
        try {
            return meals.quote(branchId, claim);
        } finally {
            MethodTiming.finish(started, MealController.class, "quote(long,Claim)");
        }
    }

    /** Returns bounded meal definitions after branch-scoped staff authorization. */
    @GetMapping("/api/admin/branches/{branchId}/meals")
    public List<Definition> admin(@PathVariable long branchId) {
        long started = MethodTiming.start(MealController.class, "admin(long)");
        try {
            return meals.catalog(branchId, true);
        } finally {
            MethodTiming.finish(started, MealController.class, "admin(long)");
        }
    }

    /** Creates a validated branch meal draft or published offer. */
    @PostMapping("/api/admin/branches/{branchId}/meals")
    public Definition create(@PathVariable long branchId, @RequestBody Edit edit) {
        long started = MethodTiming.start(MealController.class, "create(long,Edit)");
        try {
            return meals.save(branchId, 0, edit);
        } finally {
            MethodTiming.finish(started, MealController.class, "create(long,Edit)");
        }
    }

    /** Updates a meal with a required matching configuration version. */
    @PutMapping("/api/admin/branches/{branchId}/meals/{id}")
    public Definition update(
            @PathVariable long branchId, @PathVariable long id, @RequestBody Edit edit) {
        long started = MethodTiming.start(MealController.class, "update(long,long,Edit)");
        try {
            return meals.save(branchId, id, edit);
        } finally {
            MethodTiming.finish(started, MealController.class, "update(long,long,Edit)");
        }
    }
}
