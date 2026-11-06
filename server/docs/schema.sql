-- keyou-e1 schema（与 DatabaseInitializer 保持同步）
-- 重建：mysql -ukeyou -p keyou_e1 < schema.sql
CREATE DATABASE IF NOT EXISTS keyou_e1
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE keyou_e1;

-- 任务 9：班级成员
CREATE TABLE IF NOT EXISTS mate (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  phone VARCHAR(20),
  email VARCHAR(100),
  gender VARCHAR(10),
  remark VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 班级群 · 公告
CREATE TABLE IF NOT EXISTS notice (
  id INT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(120) NOT NULL,
  content TEXT,
  author VARCHAR(50),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 班级群 · 消息
CREATE TABLE IF NOT EXISTS chat_message (
  id INT PRIMARY KEY AUTO_INCREMENT,
  sender VARCHAR(50),
  content TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 班级群 · 文件元数据（文件本体在 ${catalina.base}/files）
CREATE TABLE IF NOT EXISTS app_file (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(200) NOT NULL,
  size BIGINT,
  uploader VARCHAR(50),
  uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_name(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
