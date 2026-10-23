export type CartUndo = {before:string;after:string;branch:string;pickup:string;pending:string;attempt:string;expiresAt:number};
export function canUndoCart(change:CartUndo,current:Omit<CartUndo,"before"|"expiresAt">,now=Date.now()):boolean {
    return now<change.expiresAt&&change.after===current.after&&change.branch===current.branch&&change.pickup===current.pickup&&change.pending===current.pending&&change.attempt===current.attempt&&!current.pending&&!current.attempt;
}
