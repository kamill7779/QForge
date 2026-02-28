CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO user_account (username, password, enabled)
SELECT 'admin', '{noop}admin123', TRUE
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE username = 'admin');
