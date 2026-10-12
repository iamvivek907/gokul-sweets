-- Existing mobile pictures remain valid; their type can be inferred from the managed URL.
ALTER TABLE homepage_campaigns ADD COLUMN mobile_media_type VARCHAR(40);
ALTER TABLE campaign_publications ADD COLUMN mobile_media_type VARCHAR(40);
