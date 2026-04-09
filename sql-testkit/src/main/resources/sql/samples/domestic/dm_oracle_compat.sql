-- 达梦 DM Database - Oracle兼容语法样本
-- 使用 ParseContext(sql, ProductDialect.DM)

-- ROWNUM分页
SELECT * FROM (SELECT ROWNUM rn, emp.* FROM employees emp WHERE ROWNUM <= 10) WHERE rn > 0;

-- Oracle风格OUTER JOIN
SELECT e.name, d.dept_name FROM employees e, departments d WHERE e.dept_id = d.id(+);

-- 达梦支持的类型
CREATE TABLE dm_types_test (
    id          INT PRIMARY KEY,
    name        VARCHAR(100),
    amount      NUMBER(15, 2),
    big_text    CLOB,
    created_at  DATE DEFAULT SYSDATE
);

-- SEQUENCE
CREATE SEQUENCE dm_seq START WITH 1 INCREMENT BY 1 NOMAXVALUE;
SELECT dm_seq.NEXTVAL FROM DUAL;
