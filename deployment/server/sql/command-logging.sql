-- Apply once to the existing application database before deploying the logger.
-- commandLog must already have the userid foreign key shown in docs/database.
ALTER TABLE `commandLog` MODIFY COLUMN `ipAddress` VARCHAR(45) NULL;

-- LIKE copies column definitions/indexes but not foreign keys.
CREATE TABLE `syncedLog` LIKE `commandLog`;
ALTER TABLE `syncedLog` ADD CONSTRAINT `syncedLog_userid`
  FOREIGN KEY (`userid`) REFERENCES `user` (`id`);

INSERT INTO `user` (`id`, `name`, `mail`, `config`)
SELECT 'user-unknown', 'Anonymous', 'info@eva-did.it',
       '{"syncDestinations":[],"isOnlineAccount":false}'
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `id` = 'user-unknown');
