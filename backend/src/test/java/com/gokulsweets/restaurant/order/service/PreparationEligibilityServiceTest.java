package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.order.config.PreparationWindowProperties;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.enums.PreparationEligibilityStatus;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class PreparationEligibilityServiceTest {

    private PreparationEligibilityService service;


    @BeforeEach
    void setUp() {

        PreparationWindowProperties properties =
                new PreparationWindowProperties();


        properties.setNormalLeadMinutes(
                60
        );


        properties.setPriorityLeadMinutes(
                30
        );


        properties.setAdminOverrideLeadMinutes(
                60
        );


        service =
                new PreparationEligibilityService(
                        properties
                );
    }


    /*
     * =========================================================
     * NORMAL — SCHEDULED
     * =========================================================
     */

    @Test
    void normalOrderBeforePreparationWindowIsScheduled() {

        Order order =
                createConfirmedOrder(
                        PickupType.NORMAL,
                        LocalDate.of(
                                2026,
                                9,
                                25
                        ),
                        LocalTime.of(
                                19,
                                30
                        )
                );


        LocalDateTime now =
                LocalDateTime.of(
                        2026,
                        9,
                        25,
                        18,
                        29
                );


        PreparationEligibility result =
                service.evaluate(
                        order,
                        now
                );


        assertEquals(
                PreparationEligibilityStatus.SCHEDULED,
                result.status()
        );


        assertFalse(
                result.canStartPreparation()
        );


        assertEquals(
                LocalDateTime.of(
                        2026,
                        9,
                        25,
                        18,
                        30
                ),
                result.eligibleAt()
        );
    }


    /*
     * =========================================================
     * NORMAL — EXACT ELIGIBILITY BOUNDARY
     * =========================================================
     */

    @Test
    void normalOrderAtEligibilityBoundaryIsEligible() {

        Order order =
                createConfirmedOrder(
                        PickupType.NORMAL,
                        LocalDate.of(
                                2026,
                                9,
                                25
                        ),
                        LocalTime.of(
                                19,
                                30
                        )
                );


        LocalDateTime now =
                LocalDateTime.of(
                        2026,
                        9,
                        25,
                        18,
                        30
                );


        PreparationEligibility result =
                service.evaluate(
                        order,
                        now
                );


        assertEquals(
                PreparationEligibilityStatus.ELIGIBLE,
                result.status()
        );


        assertTrue(
                result.canStartPreparation()
        );


        assertEquals(
                60,
                result.minutesUntilPickup()
        );
    }


    /*
     * =========================================================
     * NORMAL — INSIDE PREPARATION WINDOW
     * =========================================================
     */

    @Test
    void normalOrderInsidePreparationWindowIsEligible() {

        Order order =
                createConfirmedOrder(
                        PickupType.NORMAL,
                        LocalDate.of(
                                2026,
                                9,
                                25
                        ),
                        LocalTime.of(
                                19,
                                30
                        )
                );


        LocalDateTime now =
                LocalDateTime.of(
                        2026,
                        9,
                        25,
                        19,
                        0
                );


        PreparationEligibility result =
                service.evaluate(
                        order,
                        now
                );


        assertEquals(
                PreparationEligibilityStatus.ELIGIBLE,
                result.status()
        );


        assertTrue(
                result.canStartPreparation()
        );


        assertEquals(
                30,
                result.minutesUntilPickup()
        );
    }


    /*
     * =========================================================
     * EXACT PICKUP BOUNDARY
     * =========================================================
     */

    @Test
    void confirmedOrderAtPickupStartIsOverdue() {

        Order order =
                createConfirmedOrder(
                        PickupType.NORMAL,
                        LocalDate.of(
                                2026,
                                9,
                                25
                        ),
                        LocalTime.of(
                                19,
                                30
                        )
                );


        LocalDateTime now =
                LocalDateTime.of(
                        2026,
                        9,
                        25,
                        19,
                        30
                );


        PreparationEligibility result =
                service.evaluate(
                        order,
                        now
                );


        assertEquals(
                PreparationEligibilityStatus.OVERDUE,
                result.status()
        );


        assertTrue(
                result.canStartPreparation()
        );


        assertEquals(
                0,
                result.minutesUntilPickup()
        );
    }


    /*
     * =========================================================
     * OVERDUE
     * =========================================================
     */

    @Test
    void confirmedOrderAfterPickupStartIsOverdue() {

        Order order =
                createConfirmedOrder(
                        PickupType.NORMAL,
                        LocalDate.of(
                                2026,
                                9,
                                25
                        ),
                        LocalTime.of(
                                19,
                                30
                        )
                );


        LocalDateTime now =
                LocalDateTime.of(
                        2026,
                        9,
                        25,
                        19,
                        45
                );


        PreparationEligibility result =
                service.evaluate(
                        order,
                        now
                );


        assertEquals(
                PreparationEligibilityStatus.OVERDUE,
                result.status()
        );


        assertTrue(
                result.canStartPreparation()
        );


        assertEquals(
                -15,
                result.minutesUntilPickup()
        );
    }


    /*
     * =========================================================
     * PRIORITY
     * =========================================================
     */

    @Test
    void priorityOrderUsesThirtyMinuteWindow() {

        Order order =
                createConfirmedOrder(
                        PickupType.PRIORITY,
                        LocalDate.of(
                                2026,
                                9,
                                25
                        ),
                        LocalTime.of(
                                19,
                                30
                        )
                );


        PreparationEligibility scheduled =
                service.evaluate(
                        order,
                        LocalDateTime.of(
                                2026,
                                9,
                                25,
                                18,
                                59
                        )
                );


        assertEquals(
                PreparationEligibilityStatus.SCHEDULED,
                scheduled.status()
        );


        assertEquals(
                LocalDateTime.of(
                        2026,
                        9,
                        25,
                        19,
                        0
                ),
                scheduled.eligibleAt()
        );


        PreparationEligibility eligible =
                service.evaluate(
                        order,
                        LocalDateTime.of(
                                2026,
                                9,
                                25,
                                19,
                                0
                        )
                );


        assertEquals(
                PreparationEligibilityStatus.ELIGIBLE,
                eligible.status()
        );


        assertTrue(
                eligible.canStartPreparation()
        );
    }


    /*
     * =========================================================
     * ADMIN OVERRIDE
     * =========================================================
     */

    @Test
    void adminOverrideUsesNormalSixtyMinuteWindow() {

        Order order =
                createConfirmedOrder(
                        PickupType.ADMIN_OVERRIDE,
                        LocalDate.of(
                                2026,
                                9,
                                25
                        ),
                        LocalTime.of(
                                19,
                                30
                        )
                );


        PreparationEligibility result =
                service.evaluate(
                        order,
                        LocalDateTime.of(
                                2026,
                                9,
                                25,
                                18,
                                30
                        )
                );


        assertEquals(
                PreparationEligibilityStatus.ELIGIBLE,
                result.status()
        );


        assertEquals(
                LocalDateTime.of(
                        2026,
                        9,
                        25,
                        18,
                        30
                ),
                result.eligibleAt()
        );


        assertTrue(
                result.canStartPreparation()
        );
    }


    /*
     * =========================================================
     * NON-CONFIRMED ORDER
     * =========================================================
     */

    @Test
    void preparingOrderIsNotApplicable() {

        Order order =
                createConfirmedOrder(
                        PickupType.NORMAL,
                        LocalDate.of(
                                2026,
                                9,
                                25
                        ),
                        LocalTime.of(
                                19,
                                30
                        )
                );


        order.setOrderStatus(
                OrderStatus.PREPARING
        );


        PreparationEligibility result =
                service.evaluate(
                        order,
                        LocalDateTime.of(
                                2026,
                                9,
                                25,
                                18,
                                45
                        )
                );


        assertEquals(
                PreparationEligibilityStatus.NOT_APPLICABLE,
                result.status()
        );


        assertFalse(
                result.canStartPreparation()
        );


        assertNull(
                result.pickupAt()
        );


        assertNull(
                result.eligibleAt()
        );
    }


    /*
     * =========================================================
     * TEST DATA
     * =========================================================
     */

    private Order createConfirmedOrder(
            PickupType pickupType,
            LocalDate pickupDate,
            LocalTime pickupStartTime
    ) {

        PickupSlot pickupSlot =
                new PickupSlot();


        pickupSlot.setSlotDate(
                pickupDate
        );


        pickupSlot.setStartTime(
                pickupStartTime
        );


        pickupSlot.setEndTime(
                pickupStartTime.plusMinutes(
                        30
                )
        );


        Order order =
                new Order();


        order.setOrderStatus(
                OrderStatus.CONFIRMED
        );


        order.setPickupType(
                pickupType
        );


        order.setPickupSlot(
                pickupSlot
        );


        return order;
    }
}