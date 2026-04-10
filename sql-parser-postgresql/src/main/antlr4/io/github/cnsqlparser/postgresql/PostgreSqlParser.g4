parser grammar PostgreSqlParser;

options { tokenVocab = PostgreSqlLexer; }

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
    ;

// ─── SELECT ───────────────────────────────────────────────────────────────────

selectStatement
    : withClause? queryExpression orderByClause? limitOffsetClause? fetchClause?
    ;

withClause
    : WITH RECURSIVE? cteDefinition (COMMA cteDefinition)*
    ;

cteDefinition
    : id AS (NOT MATERIALIZED | MATERIALIZED)? LPAREN queryExpression RPAREN
    | id LPAREN columnList RPAREN AS (NOT MATERIALIZED | MATERIALIZED)? LPAREN queryExpression RPAREN
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
      windowClause?
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
    | LATERAL? LPAREN queryExpression RPAREN AS? id
    | LPAREN tableRef (COMMA tableRef)* RPAREN
    ;

joinPart
    : joinType JOIN tableFactor (ON expression | USING LPAREN columnList RPAREN)?
    | NATURAL joinType? JOIN tableFactor
    | CROSS JOIN tableFactor
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    ;

whereClause : WHERE expression;

groupByClause
    : GROUP BY groupByItem (COMMA groupByItem)*
    ;

groupByItem : expression (ASC | DESC_KW)? (NULLS (FIRST_KW | LAST))?;

havingClause : HAVING expression;

windowClause
    : WINDOW id AS LPAREN windowSpec RPAREN (COMMA id AS LPAREN windowSpec RPAREN)*
    ;

orderByClause
    : ORDER BY orderByItem (COMMA orderByItem)*
    ;

orderByItem
    : expression (ASC | DESC_KW)? (NULLS (FIRST_KW | LAST))?
    ;

limitOffsetClause
    : LIMIT (expression | ALL) (OFFSET_KW expression)?
    | OFFSET_KW expression (LIMIT (expression | ALL))?
    ;

fetchClause
    : FETCH (FIRST | NEXT) expression? (ROW_KW | ROWS) (ONLY | TIES)
    ;

// ─── INSERT ───────────────────────────────────────────────────────────────────

insertStatement
    : withClause? INSERT INTO tableName (AS id)?
      (LPAREN columnList RPAREN)?
      (VALUES valueList | selectStatement | DEFAULT_KW VALUES)
      onConflictClause?
      returningClause?
    ;

onConflictClause
    : ON CONFLICT (LPAREN columnList RPAREN | onConstraint)?
      DO (NOTHING | UPDATE SET assignment (COMMA assignment)* (WHERE expression)?)
    ;

onConstraint : ON CONSTRAINT id;

returningClause : RETURNING selectItems;

valueList
    : LPAREN expressionOrDefaultList RPAREN (COMMA LPAREN expressionOrDefaultList RPAREN)*
    ;

expressionOrDefaultList
    : expressionOrDefault (COMMA expressionOrDefault)*
    ;

expressionOrDefault : expression | DEFAULT_KW;

// ─── UPDATE ───────────────────────────────────────────────────────────────────

updateStatement
    : withClause? UPDATE tableName (AS id)?
      SET assignment (COMMA assignment)*
      fromClause?
      whereClause?
      returningClause?
    ;

assignment
    : columnRef EQ expression
    | columnRef EQ DEFAULT_KW
    ;

// ─── DELETE ───────────────────────────────────────────────────────────────────

deleteStatement
    : withClause? DELETE FROM tableName (AS id)?
      usingClause?
      whereClause?
      returningClause?
    ;

usingClause : USING tableRef (COMMA tableRef)*;

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
    : id (LPAREN INTEGER_LITERAL (COMMA INTEGER_LITERAL)? RPAREN)?
    | id LBRACKET RBRACKET
    ;

columnConstraint
    : NOT NULL_KW
    | NULL_KW
    | DEFAULT_KW expression
    | UNIQUE
    | PRIMARY KEY
    | REFERENCES tableName (LPAREN id RPAREN)?
    ;

tableConstraintDef
    : (CONSTRAINT id?)? PRIMARY KEY LPAREN columnList RPAREN
    | (CONSTRAINT id?)? UNIQUE LPAREN columnList RPAREN
    | (CONSTRAINT id?)? FOREIGN KEY LPAREN columnList RPAREN REFERENCES tableName LPAREN columnList RPAREN
    ;

tableOption
    : id (EQ? (id | INTEGER_LITERAL | STRING_LITERAL))?
    ;

// ─── ALTER TABLE ─────────────────────────────────────────────────────────────

alterTableStatement
    : ALTER TABLE tableName alterAction (COMMA alterAction)*
    ;

alterAction
    : ADD COLUMN? id dataType columnConstraint*
    | ADD tableConstraintDef
    | DROP COLUMN? id
    | ALTER COLUMN? id SET DEFAULT_KW expression
    | ALTER COLUMN? id DROP DEFAULT_KW
    | RENAME COLUMN id TO id
    | RENAME TO tableName
    ;

// ─── DROP ─────────────────────────────────────────────────────────────────────

dropStatement
    : DROP TABLE (IF EXISTS)? tableName (COMMA tableName)*
    | DROP VIEW (IF EXISTS)? tableName (COMMA tableName)*
    | DROP SEQUENCE (IF EXISTS)? tableName (COMMA tableName)*
    | DROP PROCEDURE (IF EXISTS)? id
    | DROP FUNCTION (IF EXISTS)? id
    | DROP TRIGGER (IF EXISTS)? id ON tableName
    | DROP SCHEMA (IF EXISTS)? id
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
    | bitExpr IS NOT? (TRUE_KW | FALSE_KW)
    | bitExpr NOT? BETWEEN bitExpr AND bitExpr
    | bitExpr NOT? LIKE bitExpr
    | bitExpr NOT? ILIKE bitExpr
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
    | bitExpr CONCAT bitExpr
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
    | simpleExpr DOUBLE_COLON dataType
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
    | functionCallExpr OVER id
    ;

windowSpec
    : (PARTITION BY expressionList)?
      orderByClause?
      windowFrame?
    ;

windowFrame
    : (ROWS | RANGE | GROUPS) frameBound
    | (ROWS | RANGE | GROUPS) BETWEEN frameBound AND frameBound
    ;

frameBound
    : CURRENT ROW_KW
    | UNBOUNDED PRECEDING
    | UNBOUNDED FOLLOWING
    | expression PRECEDING
    | expression FOLLOWING
    ;

functionCallExpr
    : id LPAREN DISTINCT? (STAR | expressionList)? RPAREN (FILTER LPAREN WHERE expression RPAREN)?
    | id DOT id LPAREN DISTINCT? (STAR | expressionList)? RPAREN
    ;

literal
    : INTEGER_LITERAL
    | DECIMAL_LITERAL
    | STRING_LITERAL
    | DOLLAR_STRING
    | HEX_LITERAL
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
    | DOUBLE_QUOTE_ID
    | NULLS | SEQUENCE | RECURSIVE | MATERIALIZED | FILTER | WINDOW | LATERAL
    | RETURNING | NOTHING | DO | CONFLICT | TIES | FETCH | NEXT | ONLY
    | FOLLOWING | PRECEDING | UNBOUNDED | CURRENT | PARTITION | GROUPS
    | FUNCTION | PROCEDURE | TRIGGER | VIEW | SCHEMA | REFERENCES | CONSTRAINT
    | OVERRIDING | TABLESAMPLE | SIMILAR | ILIKE
    ;
