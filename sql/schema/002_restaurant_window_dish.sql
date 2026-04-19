-- Restaurant, dining window, and dish base schema.
-- Owner: member B.
-- MySQL version: 8.0+

CREATE TABLE IF NOT EXISTS restaurant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Restaurant primary key',
    name VARCHAR(100) NOT NULL COMMENT 'Restaurant name',
    location VARCHAR(255) NOT NULL COMMENT 'Restaurant location',
    business_hours VARCHAR(100) NOT NULL COMMENT 'Business hours, for example 06:30-20:30',
    capacity INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Maximum dining capacity',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN, CLOSED, RESTING',
    score DECIMAL(3, 2) NOT NULL DEFAULT 0.00 COMMENT 'Average score, maintained after rating updates',
    description VARCHAR(1000) NULL COMMENT 'Restaurant description',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (id),
    KEY idx_restaurant_status (status),
    KEY idx_restaurant_name (name),
    CONSTRAINT chk_restaurant_capacity_non_negative CHECK (capacity >= 0),
    CONSTRAINT chk_restaurant_score_range CHECK (score >= 0.00 AND score <= 5.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Restaurant base information';

CREATE TABLE IF NOT EXISTS dining_window (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Dining window primary key',
    restaurant_id BIGINT UNSIGNED NOT NULL COMMENT 'Restaurant id',
    name VARCHAR(100) NOT NULL COMMENT 'Window name',
    type VARCHAR(60) NOT NULL COMMENT 'Window type, for example rice, noodles, hotpot',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN, CLOSED, RESTING',
    avg_service_time INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Average service time in seconds',
    score DECIMAL(3, 2) NOT NULL DEFAULT 0.00 COMMENT 'Average score, maintained after rating updates',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dining_window_id_restaurant_id (id, restaurant_id),
    KEY idx_dining_window_restaurant_id (restaurant_id),
    KEY idx_dining_window_status (status),
    CONSTRAINT fk_dining_window_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_dining_window_service_time_non_negative CHECK (avg_service_time >= 0),
    CONSTRAINT chk_dining_window_score_range CHECK (score >= 0.00 AND score <= 5.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Restaurant dining window information';

CREATE TABLE IF NOT EXISTS dish (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Dish primary key',
    restaurant_id BIGINT UNSIGNED NOT NULL COMMENT 'Restaurant id',
    window_id BIGINT UNSIGNED NOT NULL COMMENT 'Dining window id',
    name VARCHAR(100) NOT NULL COMMENT 'Dish name',
    price DECIMAL(10, 2) NOT NULL COMMENT 'Dish price',
    tags JSON NOT NULL COMMENT 'Dish tags as a JSON array, for example ["rice", "spicy"]',
    stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Dish stock maintained by member B',
    score DECIMAL(3, 2) NOT NULL DEFAULT 0.00 COMMENT 'Average score, maintained after rating updates',
    image_url VARCHAR(500) NULL COMMENT 'Dish image URL',
    description VARCHAR(1000) NULL COMMENT 'Dish description',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (id),
    KEY idx_dish_restaurant_id (restaurant_id),
    KEY idx_dish_window_id (window_id),
    KEY idx_dish_name (name),
    CONSTRAINT fk_dish_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_dish_window_restaurant
        FOREIGN KEY (window_id, restaurant_id) REFERENCES dining_window (id, restaurant_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_dish_price_non_negative CHECK (price >= 0.00),
    CONSTRAINT chk_dish_stock_non_negative CHECK (stock >= 0),
    CONSTRAINT chk_dish_score_range CHECK (score >= 0.00 AND score <= 5.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Dish base information and stock';

