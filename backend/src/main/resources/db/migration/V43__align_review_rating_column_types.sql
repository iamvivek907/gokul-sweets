/*
 * V42 created these columns as SMALLINT.
 *
 * Review.overallRating and ReviewItem.rating use Java Integer,
 * so Hibernate expects PostgreSQL INTEGER during schema validation.
 *
 * Keep V42 unchanged because it has already been applied.
 */

ALTER TABLE reviews
    ALTER COLUMN overall_rating
        TYPE INTEGER
        USING overall_rating::INTEGER;

ALTER TABLE review_items
    ALTER COLUMN rating
        TYPE INTEGER
        USING rating::INTEGER;
