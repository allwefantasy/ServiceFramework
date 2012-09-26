CREATE TABLE `tag_wiki` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `content` varchar(255) DEFAULT NULL,
  `tag_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id` (`id`),
  UNIQUE KEY `name` (`content`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8;

CREATE TABLE `tag_synonym` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8;

CREATE TABLE `tag_group` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id` (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8;

CREATE TABLE `tag_tag_group` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `tags_id` int(11) NOT NULL,
  `tag_groups_id` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `tag` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `tag_synonym_id` int(11) DEFAULT NULL,
  `weight` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id` (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8;

CREATE TABLE `tag_relation` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `tag_id` int(11) DEFAULT NULL,
  `object_id` int(11) DEFAULT NULL,
  `created_at` bigint(20) DEFAULT NULL,
  `weight` double DEFAULT NULL,
  `views` int(11) DEFAULT NULL,
  `comments` int(11) DEFAULT NULL,
  `content_length` int(11) DEFAULT NULL,
  `frequency` int(11) DEFAULT NULL,
  `channel_id` int(11) NOT NULL DEFAULT '-1',
  `discriminator` char(20) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `created_at_index` (`created_at`),
  KEY `weight_index` (`weight`),
  KEY `tag_id_index` (`tag_id`),
  KEY `object_id_index` (`object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;