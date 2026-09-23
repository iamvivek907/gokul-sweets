import {test} from "node:test";
import assert from "node:assert/strict";
import {visibleCampaigns, toIndiaDateTimeInput, fromIndiaDateTimeInput} from "../lib/campaigns.ts";

test("campaign schedule is start-inclusive, end-exclusive and falls through by priority", () => {
    const now = Date.parse("2026-10-01T00:00:00Z");
    const base = {active: true, mediaUrl: "/test.png", displayOrder: 1, startAt: null, endAt: null};
    const campaigns = [
        {...base, id: 1, endAt: new Date(now).toISOString()},
        {...base, id: 3},
        {...base, id: 2, startAt: new Date(now).toISOString()},
        {...base, id: 4, active: false},
        {...base, id: 5, mediaUrl: null}
    ];
    assert.deepEqual(visibleCampaigns(campaigns, now).map(value => value.id), [2, 3]);
    assert.equal(campaigns[0].id, 1);
});

test("India campaign times round-trip independently of browser timezone", () => {
    const instant = fromIndiaDateTimeInput("2026-10-01T00:00");
    assert.equal(instant, "2026-09-30T18:30:00.000Z");
    assert.equal(toIndiaDateTimeInput(instant), "2026-10-01T00:00");
    assert.equal(fromIndiaDateTimeInput(""), null);
    assert.equal(toIndiaDateTimeInput(null), "");
});
