-- 晴日小筑 · H2 (MySQL 兼容模式) 建表脚本
CREATE TABLE IF NOT EXISTS blog_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    avatar VARCHAR(500),
    role VARCHAR(20) DEFAULT 'ADMIN',
    created_at TIMESTAMP,
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS blog_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    description VARCHAR(200),
    color VARCHAR(30),
    sort_order INT DEFAULT 99,
    created_at TIMESTAMP,
    CONSTRAINT uk_category_name UNIQUE (name),
    CONSTRAINT uk_category_slug UNIQUE (slug)
);

CREATE TABLE IF NOT EXISTS blog_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    color VARCHAR(30),
    created_at TIMESTAMP,
    CONSTRAINT uk_tag_name UNIQUE (name),
    CONSTRAINT uk_tag_slug UNIQUE (slug)
);

CREATE TABLE IF NOT EXISTS blog_post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    summary VARCHAR(500),
    content TEXT NOT NULL,
    cover_image VARCHAR(500),
    category_id BIGINT,
    status TINYINT DEFAULT 0,
    is_top BOOLEAN DEFAULT FALSE,
    view_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    reading_time INT DEFAULT 1,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    published_at TIMESTAMP,
    CONSTRAINT uk_post_slug UNIQUE (slug)
);

CREATE TABLE IF NOT EXISTS blog_post_tag (
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, tag_id)
);

CREATE TABLE IF NOT EXISTS blog_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author VARCHAR(40) NOT NULL,
    email VARCHAR(120),
    website VARCHAR(200),
    content VARCHAR(1000) NOT NULL,
    status TINYINT DEFAULT 0,
    is_admin BOOLEAN DEFAULT FALSE,
    parent_id BIGINT,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS blog_guestbook (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author VARCHAR(40) NOT NULL,
    email VARCHAR(120),
    content VARCHAR(1000) NOT NULL,
    reply VARCHAR(1000),
    status TINYINT DEFAULT 0,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS blog_site_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nickname VARCHAR(40) NOT NULL,
    avatar VARCHAR(500),
    bio VARCHAR(300),
    slogan VARCHAR(200),
    location VARCHAR(80),
    email VARCHAR(120),
    github VARCHAR(300),
    about_content TEXT,
    footer_text VARCHAR(200),
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_post_status_published ON blog_post (status, published_at);
CREATE INDEX IF NOT EXISTS idx_post_category ON blog_post (category_id);
CREATE INDEX IF NOT EXISTS idx_comment_post_status ON blog_comment (post_id, status);
CREATE INDEX IF NOT EXISTS idx_guestbook_status ON blog_guestbook (status);
CREATE INDEX IF NOT EXISTS idx_post_tag_tag ON blog_post_tag (tag_id);

-- 3D 数字人
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
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_dh_character_code UNIQUE (character_code)
);

CREATE TABLE IF NOT EXISTS dh_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_name VARCHAR(100),
    character_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dh_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    emotion VARCHAR(20),
    audio_url VARCHAR(500),
    created_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dh_session_character ON dh_session (character_code);
CREATE INDEX IF NOT EXISTS idx_dh_message_session ON dh_message (session_id, created_at);

