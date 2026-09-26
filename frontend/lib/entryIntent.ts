/** The entry appears at each opening of the home route; a choice advances only this mounted visit. */
export function shouldShowIntentGateway(enabled: boolean, enteredThisOpening: boolean): boolean {
    return enabled && !enteredThisOpening;
}
