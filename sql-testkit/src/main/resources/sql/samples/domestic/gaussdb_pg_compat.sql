-- GaussDB / openGauss - PostgreSQL兼容语法样本
-- 使用 ParseContext(sql, ProductDialect.GAUSSDB)

-- PostgreSQL风格CTE
WITH RECURSIVE org_tree AS (
    SELECT id, parent_id, name, 0 AS depth FROM organizations WHERE parent_id IS NULL
    UNION ALL
    SELECT o.id, o.parent_id, o.name, ot.depth + 1
    FROM organizations o JOIN org_tree ot ON o.parent_id = ot.id
)
SELECT * FROM org_tree ORDER BY depth, name;

-- Window函数（PG风格）
SELECT emp_id, dept_id, salary,
       RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS salary_rank
FROM employees;

-- RETURNING子句（PG特有）
INSERT INTO audit_log (action, user_id, ts) VALUES ('LOGIN', 42, NOW()) RETURNING id;

-- PG风格JSON操作
SELECT data->>'name' AS name, data->>'age' AS age FROM user_profiles WHERE data->>'active' = 'true';
