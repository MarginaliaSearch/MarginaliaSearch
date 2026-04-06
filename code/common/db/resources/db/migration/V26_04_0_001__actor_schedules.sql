CREATE TABLE IF NOT EXISTS ACTOR_SCHEDULE (
    SCHEDULE_NAME VARCHAR(64) PRIMARY KEY,
    SCHEDULE_TYPE VARCHAR(16) NOT NULL,
    START_HOURS_UTC INT NULL,
    END_HOURS_UTC INT NULL,
    INTERVAL_HOURS INT NULL,
    DESCRIPTION VARCHAR(255) NOT NULL
);

-- WINDOW:  actor runs continuously for a window
-- TRIGGER: actor triggers at a a specified hour and runs until its work is done
-- INTERVAL: actor repeats on a fixed cycle

INSERT INTO ACTOR_SCHEDULE (SCHEDULE_NAME, SCHEDULE_TYPE, START_HOURS_UTC, END_HOURS_UTC, INTERVAL_HOURS, DESCRIPTION) VALUES
    ('LIVE_CRAWLER', 'TRIGGER',   0, NULL, NULL, 'Live crawler trigger (REALTIME nodes)'),
    ('DOMAIN_PING',  'WINDOW',    3,    9, NULL, 'Domain ping window (REALTIME nodes)'),
    ('RSS_FEEDS',    'TRIGGER',  12, NULL, NULL, 'RSS feed update trigger (REALTIME nodes)'),
    ('DOM_SAMPLE',   'WINDOW',   16,   20, NULL, 'DOM sample capture window (REALTIME nodes)'),
    ('SCREENGRAB',   'WINDOW',   20,    0, NULL, 'Screenshot capture window (REALTIME nodes)'),
    ('MAINTENANCE',  'TRIGGER',   2, NULL, NULL, 'Maintenance trigger (BATCH/MIXED nodes)'),
    ('SCRAPE_FEEDS', 'INTERVAL', NULL, NULL,  6, 'Link aggregator scraping interval (REALTIME nodes)');
