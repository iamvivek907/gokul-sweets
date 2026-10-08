package com.gokulsweets.restaurant.inventory.automation.dto;

import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationOutcome;

import java.time.LocalDate;

/**
 * Immutable automation run explanation data contract.
 *
 * @param productName the product name
 * @param outcome the outcome
 * @param message the message
 * @param firstDate the first date
 * @param lastDate the last date
 * @param dates the dates
 */
public record AutomationRunExplanation(
        String productName,
        InventoryAutomationOutcome outcome,
        String message,
        LocalDate firstDate,
        LocalDate lastDate,
        Long dates) {}
