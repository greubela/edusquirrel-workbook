-- Query contract for SqlUserCommands. Only used in the disposable test database.
-- This fixture is not a migration or an assertion about deployed table indexes.
CREATE TABLE IF NOT EXISTS `user` (
  `id` VARCHAR(255) NOT NULL PRIMARY KEY,
  `name` VARCHAR(255) NOT NULL,
  `mail` VARCHAR(255) NOT NULL,
  `config` LONGTEXT NOT NULL,
  INDEX `user_mail` (`mail`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `loginTokens` (
  `userId` VARCHAR(255) NOT NULL PRIMARY KEY,
  `token` VARCHAR(255) NOT NULL,
  `tokenExpires` DATETIME(6) NOT NULL,
  FOREIGN KEY (`userId`) REFERENCES `user` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
