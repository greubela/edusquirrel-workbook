-- EduSquirrel table setup for MySQL 8.4, inside an existing empty database.
-- Select the target in your SQL client, or run:
-- mysql --host=HOST --user=USER --password --database=NAME < setup.sql
-- This script never creates or switches databases.

-- Refuse every existing table or view before creating application tables.
-- Run as a normal batch without --force (which intentionally ignores SQL errors).
-- This assertion uses only a temporary table; it leaves no stored routine behind.
CREATE TEMPORARY TABLE `_edusquirrel_setup_guard` (
  `existing_table_count` BIGINT NOT NULL,
  CONSTRAINT `setup_requires_empty_database` CHECK (`existing_table_count` = 0)
);
INSERT INTO `_edusquirrel_setup_guard` (`existing_table_count`)
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();
DROP TEMPORARY TABLE `_edusquirrel_setup_guard`;

-- BEGIN APPLICATION TABLES
-- Account identity and login codes.
CREATE TABLE `user` (
  `id` VARCHAR(128) NOT NULL PRIMARY KEY,
  `name` VARCHAR(255) NOT NULL,
  `mail` VARCHAR(255) NOT NULL,
  `config` LONGTEXT NOT NULL,
  INDEX `user_mail` (`mail`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
CREATE TABLE `loginTokens` (
  `userId` VARCHAR(128) NOT NULL PRIMARY KEY,
  `token` VARCHAR(255) NOT NULL,
  `tokenExpires` DATETIME(6) NOT NULL,
  CONSTRAINT `loginTokens_user` FOREIGN KEY (`userId`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
INSERT INTO `user` (`id`, `name`, `mail`, `config`)
VALUES ('user-unknown', 'Anonymous', 'info@eva-did.it', '{"syncDestinations":[],"isOnlineAccount":false}');

-- Command and synchronization audit logs.
CREATE TABLE `commandLog` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `timestamp` TIMESTAMP NULL,
  `origin` VARCHAR(128) NULL,
  `type` VARCHAR(128) NULL,
  `payload` LONGTEXT NULL,
  `userid` VARCHAR(128) NULL,
  `ipAddress` VARCHAR(45) NULL,
  CONSTRAINT `commandLog_user` FOREIGN KEY (`userid`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
CREATE TABLE `syncedLog` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `timestamp` TIMESTAMP NULL,
  `origin` VARCHAR(128) NULL,
  `type` VARCHAR(128) NULL,
  `payload` LONGTEXT NULL,
  `userid` VARCHAR(128) NULL,
  `ipAddress` VARCHAR(45) NULL,
  CONSTRAINT `syncedLog_user` FOREIGN KEY (`userid`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

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
