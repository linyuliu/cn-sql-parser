parser grammar MySqlParser;

options { tokenVocab = MySqlLexer; }

// ─── Entry point ─────────────────────────────────────────────────────────────

root
    : sqlStatement (SEMI sqlStatement)* SEMI? EOF
    ;

sqlStatement
    : selectStatement
    | insertStatement
    | updateStatement
    | deleteStatement
    | createTableStatement
    | alterTableStatement
    | dropStatement
    | useStatement
    ;

// ─── SELECT ───────────────────────────────────────────────────────────────────

selectStatement
    : withClause? queryExpression orderByClause? limitClause?
    ;

withClause
    : WITH cteDefinition (COMMA cteDefinition)*
    ;

cteDefinition
    : id AS LPAREN queryExpression RPAREN
    | id LPAREN columnList RPAREN AS LPAREN queryExpression RPAREN
    ;

queryExpression
    : queryBlock setOperator queryExpression
    | queryBlock
    | LPAREN queryExpression RPAREN
    ;

setOperator
    : UNION ALL?
    | UNION DISTINCT
    | INTERSECT ALL?
    | EXCEPT ALL?
    ;

queryBlock
    : SELECT DISTINCT? selectItems
      fromClause?
      whereClause?
      groupByClause?
      havingClause?
    ;

selectItems
    : selectItem (COMMA selectItem)*
    ;

selectItem
    : STAR
    | id DOT STAR
    | expression (AS? id)?
    ;

fromClause
    : FROM tableRef (COMMA tableRef)*
    ;

tableRef
    : tableFactor joinPart*
    ;

tableFactor
    : tableName (AS? id)?
    | LPAREN queryExpression RPAREN AS? id
    | LPAREN tableRef (COMMA tableRef)* RPAREN
    ;

joinPart
    : joinType JOIN tableFactor (ON expression | USING LPAREN columnList RPAREN)?
    | NATURAL JOIN tableFactor
    | CROSS JOIN tableFactor
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | /* empty */
    ;

whereClause : WHERE expression;

groupByClause
    : GROUP BY groupByItem (COMMA groupByItem)* (WITH ROLLUP)?
    ;

groupByItem : expression (ASC | DESC_KW)?;

havingClause : HAVING expression;

orderByClause
    : ORDER BY orderByItem (COMMA orderByItem)*
    ;

orderByItem
    : expression (ASC | DESC_KW)?
    ;

limitClause
    : LIMIT expression (COMMA expression)?
    | LIMIT expression OFFSET_KW expression
    ;

// ─── INSERT ───────────────────────────────────────────────────────────────────

insertStatement
    : INSERT insertModifier? INTO? tableName
      (LPAREN columnList RPAREN)?
      (VALUES | VALUE_KW) valueList
      onDuplicateKeyClause?
    | INSERT insertModifier? INTO? tableName
      (LPAREN columnList RPAREN)?
      selectStatement
    ;

insertModifier
    : LOW_PRIORITY
    | HIGH_PRIORITY
    | DELAYED
    | IGNORE
    ;

onDuplicateKeyClause
    : ON DUPLICATE KEY SET assignment (COMMA assignment)*
    ;

valueList
    : LPAREN expressionList RPAREN (COMMA LPAREN expressionList RPAREN)*
    ;

// ─── UPDATE ───────────────────────────────────────────────────────────────────

updateStatement
    : UPDATE IGNORE? tableName (AS? id)?
      SET assignment (COMMA assignment)*
      whereClause?
      orderByClause?
      limitClause?
    ;

assignment : columnRef EQ expression;

// ─── DELETE ───────────────────────────────────────────────────────────────────

deleteStatement
    : DELETE FROM tableName (AS? id)?
      whereClause?
      orderByClause?
      limitClause?
    ;

// ─── CREATE TABLE ─────────────────────────────────────────────────────────────

createTableStatement
    : CREATE TABLE (IF NOT EXISTS)? tableName
      LPAREN createDefinition (COMMA createDefinition)* RPAREN
      tableOption*
    | CREATE TABLE (IF NOT EXISTS)? tableName tableOption* AS selectStatement
    ;

createDefinition
    : id dataType columnConstraint*
    | tableConstraintDef
    ;

dataType
    : id (LPAREN INTEGER_LITERAL (COMMA INTEGER_LITERAL)? RPAREN)? UNSIGNED?
    ;

columnConstraint
    : NOT NULL_KW
    | NULL_KW
    | DEFAULT_KW expression
    | AUTO_INCREMENT
    | PRIMARY KEY
    | UNIQUE KEY?
    | COMMENT_KW STRING_LITERAL
    ;

tableConstraintDef
    : (CONSTRAINT id?)? PRIMARY KEY LPAREN columnList RPAREN
    | (CONSTRAINT id?)? UNIQUE KEY? id? LPAREN columnList RPAREN
    | (CONSTRAINT id?)? FOREIGN KEY id? LPAREN columnList RPAREN REFERENCES tableName LPAREN columnList RPAREN
    | INDEX id? LPAREN columnList RPAREN
    ;

tableOption
    : ENGINE EQ? id
    | CHARSET EQ? id
    | COLLATE EQ? id
    | COMMENT_KW EQ? STRING_LITERAL
    | id (EQ? (id | INTEGER_LITERAL | STRING_LITERAL))?
    ;

// ─── ALTER TABLE ─────────────────────────────────────────────────────────────

alterTableStatement
    : ALTER TABLE tableName alterAction (COMMA alterAction)*
    ;

alterAction
    : ADD COLUMN? id dataType columnConstraint*
    | ADD tableConstraintDef
    | DROP COLUMN? id
    | MODIFY COLUMN? id dataType columnConstraint*
    | CHANGE COLUMN? id id dataType columnConstraint*
    | RENAME COLUMN id TO id
    | RENAME TO tableName
    | DROP PRIMARY KEY
    | DROP INDEX id
    ;

// ─── DROP ─────────────────────────────────────────────────────────────────────

dropStatement
    : DROP TABLE (IF EXISTS)? tableName
    | DROP VIEW (IF EXISTS)? tableName
    | DROP INDEX id ON tableName
    | DROP PROCEDURE (IF EXISTS)? id
    | DROP FUNCTION (IF EXISTS)? id
    | DROP TRIGGER (IF EXISTS)? id
    | DROP DATABASE (IF EXISTS)? id
    | DROP SCHEMA (IF EXISTS)? id
    ;

// ─── USE ─────────────────────────────────────────────────────────────────────

useStatement
    : USE id
    ;

// ─── Expressions ─────────────────────────────────────────────────────────────

expression
    : expression AND expression
    | expression OR expression
    | NOT expression
    | predicate
    ;

predicate
    : bitExpr IS NOT? NULL_KW
    | bitExpr NOT? BETWEEN bitExpr AND bitExpr
    | bitExpr NOT? LIKE bitExpr
    | bitExpr NOT? IN LPAREN expressionList RPAREN
    | bitExpr NOT? IN LPAREN selectStatement RPAREN
    | NOT? EXISTS LPAREN selectStatement RPAREN
    | bitExpr compOp bitExpr
    | bitExpr
    ;

compOp : EQ | NEQ | LT | GT | LTE | GTE;

bitExpr
    : bitExpr BITAND bitExpr
    | bitExpr BITOR bitExpr
    | bitExpr BITXOR bitExpr
    | bitExpr LSHIFT bitExpr
    | bitExpr RSHIFT bitExpr
    | addExpr
    ;

addExpr
    : addExpr PLUS mulExpr
    | addExpr MINUS mulExpr
    | mulExpr
    ;

mulExpr
    : mulExpr STAR unaryExpr
    | mulExpr DIVIDE unaryExpr
    | mulExpr MOD unaryExpr
    | unaryExpr
    ;

unaryExpr
    : MINUS unaryExpr
    | BITNOT unaryExpr
    | NOT unaryExpr
    | simpleExpr
    ;

simpleExpr
    : literal
    | columnRef
    | functionCallExpr
    | caseExpression
    | castExpression
    | windowFunctionExpr
    | LPAREN expression RPAREN
    | LPAREN selectStatement RPAREN
    | STAR
    | id DOT STAR
    ;

caseExpression
    : CASE expression? whenClause+ elseClause? END
    ;

whenClause : WHEN expression THEN expression;
elseClause : ELSE expression;

castExpression
    : CAST LPAREN expression AS dataType RPAREN
    ;

windowFunctionExpr
    : functionCallExpr OVER LPAREN windowSpec RPAREN
    ;

windowSpec
    : (PARTITION BY expressionList)?
      orderByClause?
      windowFrame?
    ;

windowFrame
    : (ROWS | RANGE) frameBound
    | (ROWS | RANGE) BETWEEN frameBound AND frameBound
    ;

frameBound
    : CURRENT ROW_KW
    | UNBOUNDED PRECEDING
    | UNBOUNDED FOLLOWING
    | expression PRECEDING
    | expression FOLLOWING
    ;

functionCallExpr
    : id LPAREN DISTINCT? (STAR | expressionList)? RPAREN
    | id DOT id LPAREN DISTINCT? (STAR | expressionList)? RPAREN
    ;

literal
    : INTEGER_LITERAL
    | DECIMAL_LITERAL
    | STRING_LITERAL
    | HEX_LITERAL
    | BIT_LITERAL
    | NULL_KW
    | TRUE_KW
    | FALSE_KW
    ;

columnRef
    : id DOT id DOT id
    | id DOT id
    | id
    ;

tableName
    : id DOT id
    | id
    ;

columnList : id (COMMA id)*;
expressionList : expression (COMMA expression)*;

id
    : ID
    | BACKTICK_ID
    | ENGINE | CHARSET | COLLATE | VALUE_KW | NULLS | ROLLUP
    | MODIFY | CHANGE | DELAYED | AUTO_INCREMENT | COMMENT_KW
    | UNSIGNED | FORCE | IGNORE | DUPLICATE | SCHEMA | DATABASE
    | VIEW | PROCEDURE | FUNCTION | TRIGGER | REFERENCES | CONSTRAINT
    | FOLLOWING | PRECEDING | UNBOUNDED | CURRENT | PARTITION
    ;
