package com.gokulsweets.restaurant.inventory.automation.dto;

import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationOutcome;
import java.time.LocalDate;

public record AutomationRunExplanation(String productName, InventoryAutomationOutcome outcome,
                                       String message, LocalDate firstDate, LocalDate lastDate, Long dates) {}
