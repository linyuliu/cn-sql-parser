-- MySQL基础DML样本
SELECT id, name, age FROM users WHERE age > 18 ORDER BY name LIMIT 10;
SELECT u.id, o.total FROM users u JOIN orders o ON u.id = o.user_id WHERE u.status = 'ACTIVE';
SELECT dept_id, COUNT(*) AS cnt FROM employees GROUP BY dept_id HAVING COUNT(*) > 5;
INSERT INTO users (name, email, age) VALUES ('张三', 'zhangsan@example.com', 25);
UPDATE users SET status = 'INACTIVE', updated_at = NOW() WHERE last_login < '2024-01-01';
DELETE FROM logs WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
