ALTER TABLE homepage_campaigns
    ADD COLUMN creation_request_id UUID UNIQUE,
    ADD COLUMN creation_request_hash VARCHAR(64),
    ADD COLUMN media_request_id UUID,
    ADD COLUMN fallback_request_id UUID;

CREATE TABLE campaign_media_requests (
    campaign_id BIGINT NOT NULL REFERENCES homepage_campaigns(id),
    fallback BOOLEAN NOT NULL,
    request_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY (campaign_id, fallback, request_id)
);
