-- Current schemas only. Formats are stable, workbook-independent names, never element IDs.
-- Exact LocalDateTime values are stored as ISO text. The day/nano index supports
-- inclusive range queries across the full LocalDateTime domain without SQL precision loss.
CREATE TABLE `sync_context_id` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `program_id` VARCHAR(128) NOT NULL,
  `scenario_id` VARCHAR(128) NOT NULL,
  `user_id` VARCHAR(128) NOT NULL,
  `variable_id` VARCHAR(255) NOT NULL,
  `format` VARCHAR(128) NOT NULL,
  UNIQUE KEY `sync_context_identity` (`program_id`, `scenario_id`, `user_id`, `variable_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE TABLE `answer_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `sync_id` BIGINT UNSIGNED NOT NULL,
  `event_id` VARCHAR(71) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_day` BIGINT NOT NULL,
  `event_nano` BIGINT NOT NULL,
  `timestamp` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `serialized_data` JSON NOT NULL,
  UNIQUE KEY `answer_identity` (`sync_id`, `event_id`),
  KEY `answer_range` (`sync_id`, `event_day`, `event_nano`),
  CONSTRAINT `answer_sync` FOREIGN KEY (`sync_id`) REFERENCES `sync_context_id` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE TABLE `grading_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `sync_id` BIGINT UNSIGNED NOT NULL,
  `event_id` VARCHAR(71) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_day` BIGINT NOT NULL,
  `event_nano` BIGINT NOT NULL,
  `timestamp` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `serialized_data` JSON NOT NULL,
  UNIQUE KEY `grading_identity` (`sync_id`, `event_id`),
  KEY `grading_range` (`sync_id`, `event_day`, `event_nano`),
  CONSTRAINT `grading_sync` FOREIGN KEY (`sync_id`) REFERENCES `sync_context_id` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE TABLE `scaffolding_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `sync_id` BIGINT UNSIGNED NOT NULL,
  `event_id` VARCHAR(71) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_day` BIGINT NOT NULL,
  `event_nano` BIGINT NOT NULL,
  `timestamp` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `serialized_data` JSON NOT NULL,
  UNIQUE KEY `scaffolding_identity` (`sync_id`, `event_id`),
  KEY `scaffolding_range` (`sync_id`, `event_day`, `event_nano`),
  CONSTRAINT `scaffolding_sync` FOREIGN KEY (`sync_id`) REFERENCES `sync_context_id` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE TABLE `blob_history` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `program_id` VARCHAR(128) NOT NULL,
  `scenario_id` VARCHAR(128) NOT NULL,
  `user_id` VARCHAR(128) NOT NULL,
  `variable_id` VARCHAR(255) NOT NULL,
  `format` VARCHAR(128) NOT NULL,
  `history_json` JSON NOT NULL,
  UNIQUE KEY `blob_identity` (`program_id`, `scenario_id`, `user_id`, `variable_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
