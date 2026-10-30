package com.offblink.keyou.server.config;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 建表 + 灌种子数据（幂等：CREATE TABLE IF NOT EXISTS + 首次空表才播种）。
 * DDL 同步落 server/docs/schema.sql，供报告与重建使用。
 */
@Component
public class DatabaseInitializer implements InitializingBean {

    private static final Log log = LogFactory.getLog(DatabaseInitializer.class);

    @Autowired
    private DataSource dataSource;

    @Override
    public void afterPropertiesSet() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS mate (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "name VARCHAR(50) NOT NULL," +
                    "phone VARCHAR(20)," +
                    "email VARCHAR(100)," +
                    "gender VARCHAR(10)," +
                    "remark VARCHAR(255)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");

            int count = rowCount(conn);
            if (count == 0) {
                st.executeUpdate("INSERT INTO mate(name, phone, email, gender, remark) VALUES " +
                    "('张三','13800000001','zhangsan@example.com','男','班长')," +
                    "('李四','13800000002','lisi@example.com','女','学习委员')," +
                    "('王五','13800000003','wangwu@example.com','男','体育委员')");
                log.info("DatabaseInitializer: seeded 3 mates");
            }
            log.info("DatabaseInitializer: schema ready (" + rowCount(conn) + " mates)");
        }
    }

    private int rowCount(Connection conn) throws Exception {
        try (var st = conn.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM mate")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
