-- OceanBase - MySQL兼容模式SQL样本
-- 使用 ParseContext(sql, ProductDialect.OCEANBASE, CompatibilityMode.MYSQL)

-- 标准MySQL DML
SELECT * FROM orders WHERE tenant_id = 1001 AND status = 'PENDING' LIMIT 100;

-- OceanBase特有：并行Hint
SELECT /*+ PARALLEL(4) */ COUNT(*) FROM big_table WHERE date_col > '2024-01-01';

-- OceanBase特有：分区查询
SELECT * FROM orders PARTITION (p2024_01) WHERE order_date = '2024-01-15';

-- OceanBase支持的CTE
WITH monthly_summary AS (
    SELECT DATE_FORMAT(order_date, '%Y-%m') AS month, SUM(amount) AS total
    FROM orders GROUP BY DATE_FORMAT(order_date, '%Y-%m')
)
SELECT * FROM monthly_summary WHERE total > 100000;
