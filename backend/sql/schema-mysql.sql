-- 晴日小筑 · MySQL 8 建表脚本
CREATE DATABASE IF NOT EXISTS sunny_blog
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE sunny_blog;

CREATE TABLE IF NOT EXISTS blog_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    avatar VARCHAR(500),
    role VARCHAR(20) DEFAULT 'ADMIN',
    created_at DATETIME,
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    description VARCHAR(200),
    color VARCHAR(30),
    sort_order INT DEFAULT 99,
    created_at DATETIME,
    UNIQUE KEY uk_category_name (name),
    UNIQUE KEY uk_category_slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    color VARCHAR(30),
    created_at DATETIME,
    UNIQUE KEY uk_tag_name (name),
    UNIQUE KEY uk_tag_slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    summary VARCHAR(500),
    content LONGTEXT NOT NULL,
    cover_image VARCHAR(500),
    category_id BIGINT,
    status TINYINT DEFAULT 0,
    is_top TINYINT(1) DEFAULT 0,
    view_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    reading_time INT DEFAULT 1,
    created_at DATETIME,
    updated_at DATETIME,
    published_at DATETIME,
    UNIQUE KEY uk_post_slug (slug),
    KEY idx_post_status_published (status, published_at),
    KEY idx_post_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_post_tag (
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, tag_id),
    KEY idx_post_tag_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author VARCHAR(40) NOT NULL,
    email VARCHAR(120),
    website VARCHAR(200),
    content VARCHAR(1000) NOT NULL,
    status TINYINT DEFAULT 0,
    is_admin TINYINT(1) DEFAULT 0,
    parent_id BIGINT,
    created_at DATETIME,
    KEY idx_comment_post_status (post_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_guestbook (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author VARCHAR(40) NOT NULL,
    email VARCHAR(120),
    content VARCHAR(1000) NOT NULL,
    reply VARCHAR(1000),
    status TINYINT DEFAULT 0,
    created_at DATETIME,
    KEY idx_guestbook_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS blog_site_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nickname VARCHAR(40) NOT NULL,
    avatar VARCHAR(500),
    bio VARCHAR(300),
    slogan VARCHAR(200),
    location VARCHAR(80),
    email VARCHAR(120),
    github VARCHAR(300),
    about_content LONGTEXT,
    footer_text VARCHAR(200),
    updated_at DATETIME
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================
-- 3D 数字人
-- ============================================================
CREATE TABLE IF NOT EXISTS dh_character (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    character_code VARCHAR(50) NOT NULL,
    character_name VARCHAR(100) NOT NULL,
    avatar_icon VARCHAR(50),
    system_prompt TEXT NOT NULL,
    tts_voice VARCHAR(128) DEFAULT 'longyingxiao_v3',
    tts_model VARCHAR(64) DEFAULT 'cosyvoice-v3-flash',
    tts_sample_rate INT DEFAULT 24000,
    model_path VARCHAR(500),
    greeting_message VARCHAR(500),
    sort_order INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at DATETIME,
    updated_at DATETIME,
    UNIQUE KEY uk_dh_character_code (character_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dh_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_name VARCHAR(100),
    character_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at DATETIME,
    updated_at DATETIME,
    KEY idx_dh_session_character (character_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dh_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    emotion VARCHAR(20),
    audio_url VARCHAR(500),
    created_at DATETIME,
    KEY idx_dh_message_session (session_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

