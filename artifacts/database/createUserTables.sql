-- Account identity and login codes. Run before createLogTables.sql.
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
