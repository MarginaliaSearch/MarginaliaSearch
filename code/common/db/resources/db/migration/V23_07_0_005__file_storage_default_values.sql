INSERT IGNORE INTO FILE_STORAGE_BASE(NAME, PATH, TYPE, PERMIT_TEMP)
VALUES
('Index Storage', '/vol', 'SSD_INDEX', false),
('Data Storage', '/samples', 'SLOW', true);

INSERT IGNORE INTO FILE_STORAGE(BASE_ID, PATH, DESCRIPTION, TYPE)
SELECT ID, 'iw', "Index Staging Area", 'INDEX_STAGING'
FROM FILE_STORAGE_BASE WHERE NAME='Index Storage';

INSERT IGNORE INTO FILE_STORAGE(BASE_ID, PATH, DESCRIPTION, TYPE)
SELECT ID, 'ir', "Index Live Area", 'INDEX_LIVE'
FROM FILE_STORAGE_BASE WHERE NAME='Index Storage';

INSERT IGNORE INTO FILE_STORAGE(BASE_ID, PATH, DESCRIPTION, TYPE)
SELECT ID, 'lw', "Lexicon Staging Area", 'LEXICON_STAGING'
FROM FILE_STORAGE_BASE WHERE NAME='Index Storage';

INSERT IGNORE INTO FILE_STORAGE(BASE_ID, PATH, DESCRIPTION, TYPE)
SELECT ID, 'lr', "Lexicon Live Area", 'LEXICON_LIVE'
FROM FILE_STORAGE_BASE WHERE NAME='Index Storage';

INSERT IGNORE INTO FILE_STORAGE(BASE_ID, PATH, DESCRIPTION, TYPE)
SELECT ID, 'ss', "Search Sets", 'SEARCH_SETS'
FROM FILE_STORAGE_BASE WHERE NAME='Index Storage';

INSERT IGNORE INTO FILE_STORAGE(BASE_ID, PATH, DESCRIPTION, TYPE)
SELECT ID, 'export', "Exported Data", 'EXPORT'
FROM FILE_STORAGE_BASE WHERE TYPE='EXPORT';