
CREATE TABLE EC_DOMAIN_NEIGHBORS (
    ID INT PRIMARY KEY AUTO_INCREMENT,
    DOMAIN_ID INT NOT NULL,
    NEIGHBOR_ID INT NOT NULL,
    ADJ_IDX INT NOT NULL,

    CONSTRAINT CONS UNIQUE (DOMAIN_ID, ADJ_IDX),
    FOREIGN KEY (DOMAIN_ID) REFERENCES EC_DOMAIN(ID) ON DELETE CASCADE
)
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE EC_DOMAIN_NEIGHBORS_2 (
    DOMAIN_ID INT NOT NULL,
    NEIGHBOR_ID INT NOT NULL,
    RELATEDNESS DOUBLE NOT NULL,

    PRIMARY KEY (DOMAIN_ID, NEIGHBOR_ID),
    FOREIGN KEY (DOMAIN_ID) REFERENCES EC_DOMAIN(ID) ON DELETE CASCADE,
    FOREIGN KEY (NEIGHBOR_ID) REFERENCES EC_DOMAIN(ID) ON DELETE CASCADE
);


CREATE OR REPLACE VIEW EC_NEIGHBORS_VIEW AS
  SELECT
    DOM.DOMAIN_NAME AS DOMAIN_NAME,
    DOM.ID AS DOMAIN_ID,
    NEIGHBOR.DOMAIN_NAME AS NEIGHBOR_NAME,
    NEIGHBOR.ID AS NEIGHBOR_ID,
    ROUND(100 * RELATEDNESS) AS RELATEDNESS
  FROM EC_DOMAIN_NEIGHBORS_2
  INNER JOIN EC_DOMAIN DOM ON DOMAIN_ID=DOM.ID
  INNER JOIN EC_DOMAIN NEIGHBOR ON NEIGHBOR_ID=NEIGHBOR.ID;
