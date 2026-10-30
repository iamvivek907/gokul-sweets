/** Business date is independent of the device timezone. */
export function inventoryToday(now=new Date()){
 return new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).format(now);
}
export function currentInventoryDate(saved:unknown,today=inventoryToday()){
 return typeof saved==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(saved)&&Number.isFinite(Date.parse(saved))&&saved>=today?saved:today;
}
export function nextInventoryMidnight(now=new Date()){
 return Math.max(1,Date.parse(`${inventoryToday(now)}T00:00:00+05:30`)+86400000-now.getTime()+50);
}
