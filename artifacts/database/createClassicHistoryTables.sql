-- Supported existing snapshot formats. No conversion to the relational format.
CREATE TABLE `events` (
  `eventid` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `programid` VARCHAR(128) NOT NULL,
  `scenarioid` VARCHAR(128) NOT NULL,
  `userid` VARCHAR(128) NOT NULL,
  `timestamp` DATETIME(6) NOT NULL,
  `serializeddata` LONGTEXT NOT NULL,
  INDEX `events_context` (`programid`, `scenarioid`, `userid`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
CREATE TABLE `eventsWithKey` (
  `eventid` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `programid` VARCHAR(128) NOT NULL,
  `scenarioid` VARCHAR(128) NOT NULL,
  `userid` VARCHAR(128) NOT NULL,
  `timestamp` DATETIME(6) NOT NULL,
  `serializeddata` LONGTEXT NOT NULL,
  `eventkey` VARCHAR(255) NOT NULL,
  UNIQUE KEY `events_key` (`programid`, `scenarioid`, `userid`, `eventkey`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
