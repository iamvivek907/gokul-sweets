package com.gokulsweets.restaurant.order.service;
import com.gokulsweets.restaurant.customer.identity.VerifiedOrderOwnership;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.rebate.RebateEligibilityService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class MobileCheckoutPreviewTest {
 final VerifiedOrderOwnership ownership=mock(VerifiedOrderOwnership.class);
 final CheckoutQuoteService quotes=mock(CheckoutQuoteService.class);
 final RebateEligibilityService rebates=mock(RebateEligibilityService.class);
 final MobileCheckoutPreview preview=new MobileCheckoutPreview(ownership,quotes,rebates);
 final CreateOrderRequest request=new CreateOrderRequest(1L,2L,"Customer","9876543210",PickupType.NORMAL,List.of());
 @Test void identityMustBeCheckedBeforePricing(){
  doThrow(new IllegalArgumentException("Verify phone")).when(ownership).requireVerifiedIdentity("9876543210","expired");
  assertThatThrownBy(()->preview.preview(request,"expired")).hasMessage("Verify phone");verifyNoInteractions(quotes,rebates);
 }
 @Test void serverQuoteBecomesAnUnsavedOfferDraftWithFeeAndIdentity(){
  var quote=new CheckoutQuoteService.Quote(List.of(),"100","5","0","10","0","117.30","INR","2099-01-01T00:00:00Z","signed","2.30","0","2");
  when(quotes.preview(request,null)).thenReturn(quote);
  when(rebates.previewDraft(any())).thenAnswer(call->{var draft=call.getArgument(0,com.gokulsweets.restaurant.order.entity.Order.class);assertThat(draft.getId()).isNull();assertThat(draft.getBranch().getId()).isEqualTo(1);assertThat(draft.getCustomerPhone()).isEqualTo("9876543210");assertThat(draft.getPaymentFeeRate()).isEqualByComparingTo("2");assertThat(draft.getConvenienceFee()).isEqualByComparingTo("10");return List.of();});
  assertThat(preview.preview(request,"verified").quote()).isEqualTo(quote);
  var sequence=inOrder(ownership,quotes,rebates);sequence.verify(ownership).requireVerifiedIdentity("9876543210","verified");sequence.verify(quotes).preview(request,null);sequence.verify(rebates).previewDraft(any());
 }
}
