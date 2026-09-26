import {test} from "node:test";
import assert from "node:assert/strict";
import {useStaticCampaignMedia} from "../lib/mediaRecovery.ts";
import {safeVital} from "../lib/vitals.ts";

test("data saving and reduced motion use the static campaign poster", () => {
    assert.equal(useStaticCampaignMedia(true, false, true, false, true), true);
    assert.equal(useStaticCampaignMedia(true, true, false, false, true), true);
    assert.equal(useStaticCampaignMedia(true, false, false, true, true), true);
    assert.equal(useStaticCampaignMedia(false, false, true, false, true), false);
});

test("only bounded anonymous timings and coarse pages are reported", () => {
    assert.deepEqual(safeVital("LCP", 2244.056, "good", "/checkout/review?order=123"),
        {name: "LCP", value: 2244.06, rating: "good", page: "checkout"});
    assert.equal(safeVital("LCP", Infinity, "good", "/"), null);
    assert.equal(safeVital("LCP", -1, "good", "/"), null);
    assert.equal(safeVital("unknown", 1, "good", "/"), null);
});
