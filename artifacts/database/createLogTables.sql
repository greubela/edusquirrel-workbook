-- Requires createUserTables.sql. Audit tables share the same columns.
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
