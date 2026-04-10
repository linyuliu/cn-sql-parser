-- 人大金仓 KingbaseES - Oracle兼容模式SQL样本
-- 使用 ParseContext(sql, ProductDialect.KINGBASE, CompatibilityMode.ORACLE)

-- ROWNUM分页（Oracle风格）
SELECT * FROM (SELECT ROWNUM rn, t.* FROM employees t WHERE ROWNUM <= 20) WHERE rn > 10;

-- Oracle序列
SELECT emp_seq.NEXTVAL FROM DUAL;
INSERT INTO employees (id, name) VALUES (emp_seq.NEXTVAL, '李四');

-- NVL函数（Oracle兼容）
SELECT NVL(commission, 0) AS comm FROM employees;

-- DECODE函数
SELECT DECODE(status, 1, 'Active', 0, 'Inactive', 'Unknown') FROM users;

-- CONNECT BY层次查询（KingbaseES Oracle模式支持）
SELECT id, parent_id, name, LEVEL
FROM org_tree
START WITH parent_id IS NULL
CONNECT BY PRIOR id = parent_id;

-- MERGE INTO（Oracle风格）
MERGE INTO target_table t
USING source_table s ON (t.id = s.id)
WHEN MATCHED THEN UPDATE SET t.name = s.name
WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id, s.name);
