package com.gokulsweets.restaurant.recommendation;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.dto.*;
import com.gokulsweets.restaurant.inventory.service.CustomerInventoryAvailabilityService;
import com.gokulsweets.restaurant.menu.MenuService;
import com.gokulsweets.restaurant.menu.dto.*;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.*;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PickupAddOnBatchTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final MenuService menu=mock(MenuService.class);
    final CustomerInventoryAvailabilityService inventory=mock(CustomerInventoryAvailabilityService.class);
    final CartAvailabilityService slots=mock(CartAvailabilityService.class);
    final EnhancementProperties flags=new EnhancementProperties();
    final LocalDate date=LocalDate.of(2026,10,1);
    final CustomerInventoryCheckRequest request=new CustomerInventoryCheckRequest(date,List.of(new CustomerInventoryCheckRequest.Item(1L,1,null)));
    final PickupAddOnService service=new PickupAddOnService(jdbc,menu,inventory,mock(OrderRepository.class),mock(OrderValidationService.class),flags,Clock.fixed(Instant.parse("2026-10-01T04:00:00Z"),ZoneOffset.UTC),slots);

    @BeforeEach @SuppressWarnings("unchecked") void catalogue() throws Exception {
        flags.setPickupAddOns(true);flags.setSmartAvailability(true);
        var products=java.util.stream.LongStream.rangeClosed(1,5).mapToObj(id->new MenuProductResponse(id,1L,"Meals","Meal "+id,null,BigDecimal.TEN,null,true,ProductSaleMode.UNIT,null,null)).toList();
        when(menu.getMenu(1L)).thenReturn(List.of(new MenuCategoryResponse(1L,"Meals",null,0,products)));
        when(jdbc.queryForObject(anyString(),eq(Boolean.class))).thenReturn(true);
        doAnswer(call->{var handler=(RowCallbackHandler)call.getArgument(1);for(long id=1;id<=5;id++){var rs=mock(ResultSet.class);when(rs.getLong(1)).thenReturn(id);when(rs.getBigDecimal(2)).thenReturn(BigDecimal.ZERO);handler.processRow(rs);}return null;}).when(jdbc).query(anyString(),any(RowCallbackHandler.class));
        doAnswer(call->call.<String>getArgument(0).startsWith("WITH")?List.of():List.of(2L,3L,4L,5L)).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
    }
    CustomerInventoryCheckResponse.Item stock(long id,boolean available){return new CustomerInventoryCheckResponse.Item(id,"Meal "+id,null,BigDecimal.ONE,BigDecimal.TEN,available,null);}
    @Test void checksAllCandidatesOnceAndKeepsGoodItemsWhenAnotherIsSoldOut() {
        when(inventory.checkRequestedDate(eq(1L),any())).thenReturn(new CustomerInventoryCheckResponse(true,date,false,null,false,List.of(stock(1,true),stock(2,false),stock(3,true),stock(4,true),stock(5,true))));
        assertThat(service.recommend(1,request,null)).extracting(s->s.product().id()).containsExactly(3L,4L,5L);
        verify(inventory,times(1)).checkRequestedDate(eq(1L),argThat(r->r.items().size()==5));
        verify(inventory,never()).check(anyLong(),any());verifyNoInteractions(slots);
    }
    void selectedSlot(int capacity,boolean cartAvailable) {
        var slot=new PickupSlotResponse(7L,1L,date,LocalTime.of(15,0),LocalTime.of(16,0),10,10-capacity,capacity,true,false,0,0,0,BigDecimal.ZERO);
        var issues=new ArrayList<CartAvailabilityService.ItemAvailability>();
        issues.add(new CartAvailabilityService.ItemAvailability(2L,"Meal 2","PIECE",BigDecimal.ONE,BigDecimal.ZERO,false,"SOLD_OUT","Sold out",null));
        if(!cartAvailable)issues.add(new CartAvailabilityService.ItemAvailability(1L,"Meal 1","PIECE",BigDecimal.ONE,BigDecimal.ZERO,false,"SOLD_OUT","Sold out",null));
        when(slots.check(eq(1L),eq(date),eq(1),anyList())).thenReturn(new CartAvailabilityService.Availability("PICKUP",date,date,List.of(new CartAvailabilityService.DateAvailability(date,false,List.of(new CartAvailabilityService.SlotAvailability(slot,false,false,"Sold out","SOLD_OUT",issues)),List.of(),null,false))));
    }
    @Test void verifiesSelectedSlotInOneBatchAndLabelsOnlyVerifiedSuggestions() {
        selectedSlot(10,true);
        var result=service.recommend(1,request,null,7L,PickupType.NORMAL);
        assertThat(result).extracting(s->s.product().id()).containsExactly(3L,4L,5L);
        assertThat(result).allMatch(PickupAddOnService.Suggestion::slotVerified);
        verify(slots,times(1)).check(eq(1L),eq(date),eq(1),argThat(items->items.size()==5));verifyNoInteractions(inventory);
    }
    @Test void fullSlotOrUnavailableCartNeverProducesAnAddition() {
        selectedSlot(0,true);assertThat(service.recommend(1,request,null,7L,PickupType.NORMAL)).isEmpty();
        selectedSlot(10,false);assertThat(service.recommend(1,request,null,7L,PickupType.NORMAL)).isEmpty();
    }
    @Test void checkoutBrowseIncludesMoreCandidatesWithoutMoreInventoryReads() {
        when(inventory.checkRequestedDate(eq(1L),any())).thenReturn(new CustomerInventoryCheckResponse(true,date,true,null,false,List.of(stock(1,true),stock(2,true),stock(3,true),stock(4,true),stock(5,true))));
        assertThat(service.recommend(1,request,null,null,null,true)).extracting(s->s.product().id()).containsExactly(2L,3L,4L,5L);
        verify(inventory,times(1)).checkRequestedDate(eq(1L),argThat(r->r.items().size()==5));
    }
    @Test @SuppressWarnings("unchecked") void firstTimeBranchBrowseUsesRealMenuWithoutInventingPopularity() {
        doReturn(List.of()).when(jdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
        when(inventory.checkRequestedDate(eq(1L),any())).thenReturn(new CustomerInventoryCheckResponse(true,date,true,null,false,List.of(stock(1,true),stock(2,true),stock(3,true),stock(4,true),stock(5,true))));
        var result=service.recommend(1,request,null,null,null,true);
        assertThat(result).extracting(s->s.product().id()).containsExactly(2L,3L,4L,5L);
        assertThat(result).allMatch(s->s.reason().equals("From the branch menu"));
        verify(inventory,times(1)).checkRequestedDate(eq(1L),any());
    }
    @Test void partialSlotContextIsRejected() {
        assertThatThrownBy(()->service.recommend(1,request,null,7L,null)).isInstanceOf(IllegalArgumentException.class);
    }
}
