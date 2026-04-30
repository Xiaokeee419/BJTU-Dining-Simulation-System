-- Task A simulation seed schema
-- Generated for the campus dining simulation module.
-- CSV seed files are stored in data/task-a/.

CREATE TABLE IF NOT EXISTS task_a_virtual_students (
  student_id VARCHAR(32) PRIMARY KEY,
  province VARCHAR(32) NOT NULL,
  region_group VARCHAR(64) NOT NULL,
  user_type VARCHAR(32) NOT NULL,
  staple_tags VARCHAR(255) NOT NULL,
  taste_tags VARCHAR(255) NOT NULL,
  food_keywords VARCHAR(255) NOT NULL,
  service_tags VARCHAR(255) NOT NULL,
  normalized_staple_tags VARCHAR(255) NOT NULL,
  normalized_taste_tags VARCHAR(255) NOT NULL,
  normalized_food_tags VARCHAR(255) NOT NULL,
  normalized_service_tags VARCHAR(255) NOT NULL,
  preference_tags VARCHAR(512) NOT NULL,
  budget_min DECIMAL(6,2) NOT NULL,
  budget_max DECIMAL(6,2) NOT NULL,
  waiting_tolerance_minutes INT NOT NULL,
  breakfast_arrival_minute INT NOT NULL,
  lunch_arrival_minute INT NOT NULL,
  dinner_arrival_minute INT NOT NULL,
  INDEX idx_task_a_students_user_type (user_type),
  INDEX idx_task_a_students_region (region_group)
);

CREATE TABLE IF NOT EXISTS task_a_restaurants (
  restaurant_id BIGINT PRIMARY KEY,
  source_code VARCHAR(16) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,
  campus_area VARCHAR(64) NOT NULL,
  location VARCHAR(128) NOT NULL,
  restaurant_type VARCHAR(128) NOT NULL,
  capacity INT NOT NULL,
  base_attraction DECIMAL(4,2) NOT NULL,
  breakfast_hours VARCHAR(64) NOT NULL,
  lunch_hours VARCHAR(64) NOT NULL,
  dinner_hours VARCHAR(64) NOT NULL,
  extended_hours VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  INDEX idx_task_a_restaurants_area (campus_area),
  INDEX idx_task_a_restaurants_status (status)
);

CREATE TABLE IF NOT EXISTS task_a_windows (
  window_id BIGINT PRIMARY KEY,
  source_window_code VARCHAR(16) NOT NULL UNIQUE,
  restaurant_id BIGINT NOT NULL,
  restaurant_name VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  category VARCHAR(64) NOT NULL,
  recommended_meal_period VARCHAR(16) NOT NULL,
  open_hours VARCHAR(128) NOT NULL,
  taste_tags VARCHAR(255) NOT NULL,
  staple_tags VARCHAR(255) NOT NULL,
  audience_tags VARCHAR(255) NOT NULL,
  friendly_tags VARCHAR(255) NOT NULL,
  normalized_taste_tags VARCHAR(255) NOT NULL,
  normalized_staple_tags VARCHAR(255) NOT NULL,
  normalized_audience_tags VARCHAR(255) NOT NULL,
  normalized_friendly_tags VARCHAR(255) NOT NULL,
  matching_tags VARCHAR(512) NOT NULL,
  price_min DECIMAL(6,2) NOT NULL,
  price_max DECIMAL(6,2) NOT NULL,
  service_rate_per_minute DECIMAL(5,2) NOT NULL,
  popularity DECIMAL(4,2) NOT NULL,
  status VARCHAR(16) NOT NULL,
  CONSTRAINT fk_task_a_windows_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES task_a_restaurants (restaurant_id),
  INDEX idx_task_a_windows_restaurant (restaurant_id),
  INDEX idx_task_a_windows_meal_period (recommended_meal_period),
  INDEX idx_task_a_windows_status (status)
);

CREATE TABLE IF NOT EXISTS task_a_dishes (
  dish_id BIGINT PRIMARY KEY,
  window_id BIGINT NOT NULL,
  restaurant_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  price DECIMAL(6,2) NOT NULL,
  prep_time_minutes INT NOT NULL,
  popularity DECIMAL(4,2) NOT NULL,
  taste_tags VARCHAR(255) NOT NULL,
  staple_tags VARCHAR(255) NOT NULL,
  friendly_tags VARCHAR(255) NOT NULL,
  normalized_taste_tags VARCHAR(255) NOT NULL,
  normalized_staple_tags VARCHAR(255) NOT NULL,
  normalized_friendly_tags VARCHAR(255) NOT NULL,
  matching_tags VARCHAR(512) NOT NULL,
  CONSTRAINT fk_task_a_dishes_window
    FOREIGN KEY (window_id) REFERENCES task_a_windows (window_id),
  CONSTRAINT fk_task_a_dishes_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES task_a_restaurants (restaurant_id),
  INDEX idx_task_a_dishes_window (window_id),
  INDEX idx_task_a_dishes_restaurant (restaurant_id),
  INDEX idx_task_a_dishes_price (price)
);

CREATE TABLE IF NOT EXISTS task_a_arrival_rules (
  meal_period VARCHAR(16) PRIMARY KEY,
  simulation_start_time VARCHAR(8) NOT NULL,
  course_reference_time VARCHAR(8) NOT NULL,
  peak_centers_minutes VARCHAR(64) NOT NULL,
  peak_weights VARCHAR(64) NOT NULL,
  standard_deviations VARCHAR(64) NOT NULL,
  description VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS task_a_tag_mappings (
  mapping_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_type VARCHAR(16) NOT NULL,
  source_tag VARCHAR(64) NOT NULL,
  normalized_tags VARCHAR(255) NOT NULL,
  description VARCHAR(128) NOT NULL,
  INDEX idx_task_a_tag_mappings_source (source_tag)
);
