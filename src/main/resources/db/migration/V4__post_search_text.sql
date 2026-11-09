-- M7 forward migration: server-derived canonical text for exact post search.
-- Nullable keeps V3-compatible rows readable while V5 backfills every existing row.
ALTER TABLE posts
    ADD COLUMN content_text LONGTEXT NULL AFTER content_html;
