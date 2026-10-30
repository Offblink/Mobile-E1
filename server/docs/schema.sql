-- keyou-e1 schema（与 DatabaseInitializer 保持同步）
-- 重建：mysql -ukeyou -p keyou_e1 < schema.sql
CREATE DATABASE IF NOT EXISTS keyou_e1
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE keyou_e1;

CREATE TABLE IF NOT EXISTS mate (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  phone VARCHAR(20),
  email VARCHAR(100),
  gender VARCHAR(10),
  remark VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
