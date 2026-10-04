/** A reservation update may have reached the server; retain the cart until rechecked. */
export class CheckoutUpdateUncertainError extends Error {
 constructor(message = "We couldn’t confirm the updated reservation. Your addition remains in the cart. Recheck the current cart and total before payment.") {
  super(message);
  this.name = "CheckoutUpdateUncertainError";
 }
}

/** An offer write may have completed; recover the server total before payment. */
export class CheckoutSavingsUncertainError extends Error {
 constructor() {
  super("We couldn’t confirm the offer update. Your reservation and cart are saved. Recheck the reserved total before payment.");
  this.name="CheckoutSavingsUncertainError";
 }
}
