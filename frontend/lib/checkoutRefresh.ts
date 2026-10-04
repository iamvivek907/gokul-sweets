/** A reservation update may have reached the server; retain the cart until rechecked. */
export class CheckoutUpdateUncertainError extends Error {
 constructor(message = "We couldn’t confirm the updated reservation. Your addition remains in the cart. Recheck the current cart and total before payment.") {
  super(message);
  this.name = "CheckoutUpdateUncertainError";
 }
}
