-- Standalone relational history storage. IDs never cross the backend boundary.
-- NO PAD binary collation preserves exact context strings, including trailing spaces.
-- Timestamps use canonical ISO local date/time with nine fractional digits:
-- yyyy-MM-ddTHH:mm:ss.nnnnnnnnn. Text preserves nanoseconds in a single column.
CREATE TABLE `sync_context_id` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `program_id` VARCHAR(128) NOT NULL,
  `scenario_id` VARCHAR(128) NOT NULL,
  `user_id` VARCHAR(128) NOT NULL,
  `variable_id` VARCHAR(255) NOT NULL,
  UNIQUE KEY `sync_context_identity` (`program_id`, `scenario_id`, `user_id`, `variable_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE TABLE `answer_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `sync_id` BIGINT UNSIGNED NOT NULL,
  `timestamp` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `update_importance` ENUM('DEFAULT','TEMPORARY','MINOR','MAJOR') NOT NULL,
  `serialized_data` JSON NOT NULL,
  UNIQUE KEY `answer_identity` (`sync_id`, `timestamp`),
  UNIQUE KEY `answer_context_id` (`sync_id`, `id`),
  CONSTRAINT `answer_sync` FOREIGN KEY (`sync_id`) REFERENCES `sync_context_id` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE TABLE `scaffolding_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `sync_id` BIGINT UNSIGNED NOT NULL,
  `answer_id` BIGINT UNSIGNED NOT NULL,
  `timestamp` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `scaffolding_entity` JSON NOT NULL,
  `scaffolding_status` VARCHAR(64) NOT NULL,
  `serialized_data` JSON NOT NULL,
  `additional_data` JSON NULL,
  UNIQUE KEY `scaffolding_identity` (`sync_id`, `answer_id`),
  CONSTRAINT `scaffolding_answer` FOREIGN KEY (`sync_id`, `answer_id`) REFERENCES `answer_event` (`sync_id`, `id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE TABLE `grading_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `sync_id` BIGINT UNSIGNED NOT NULL,
  `answer_id` BIGINT UNSIGNED NOT NULL,
  `grader_key` VARCHAR(300) NOT NULL,
  `grader_definition` JSON NOT NULL,
  `grading_entity` JSON NOT NULL,
  `grading_status` VARCHAR(64) NOT NULL,
  `timestamp` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `serialized_data` JSON NOT NULL,
  `additional_data` JSON NULL,
  UNIQUE KEY `grading_identity` (`sync_id`, `answer_id`, `grader_key`),
  CONSTRAINT `grading_answer` FOREIGN KEY (`sync_id`, `answer_id`) REFERENCES `answer_event` (`sync_id`, `id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
