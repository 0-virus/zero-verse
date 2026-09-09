-- Keep V1 rows and make category name/order reusable after soft delete.
ALTER TABLE categories
    DROP INDEX uk_categories_blog_parent_name,
    DROP INDEX uk_categories_blog_parent_order;

ALTER TABLE categories
    ADD COLUMN active_key TINYINT
        AS (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED AFTER parent_key;

ALTER TABLE categories
    ADD UNIQUE KEY uk_categories_blog_parent_name_active
        (blog_id, parent_key, name, active_key),
    ADD UNIQUE KEY uk_categories_blog_parent_order_active
        (blog_id, parent_key, display_order, active_key);
