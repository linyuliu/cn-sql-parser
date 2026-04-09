package io.github.cnsqlparser.antlr

import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ParsePosition
import io.github.cnsqlparser.common.SqlSyntaxException
import io.github.cnsqlparser.model.ParseResult
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.slf4j.LoggerFactory

/**
 * ANTLR4解析器适配器基类
 *
 * Base adapter for ANTLR4-based dialect parsers.
 * Provides a consistent error-listener strategy and tolerant-mode support.
 */
abstract class AntlrParserAdapter {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 解析入口
     */
    fun parse(ctx: ParseContext): ParseResult {
        val inputStream = CharStreams.fromString(ctx.sql)
        val lexer = createLexer(inputStream)
        val tokens = CommonTokenStream(lexer)
        val parser = createParser(tokens)

        val warnings = mutableListOf<String>()
        val errorListener = CollectingErrorListener(ctx.tolerant, warnings)

        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)

        return try {
            buildResult(ctx, parser, warnings)
        } catch (e: ParseCancellationException) {
            throw SqlSyntaxException(
                "SQL syntax error: ${e.message}",
                cause = e
            )
        }
    }

    protected abstract fun createLexer(input: CharStream): Lexer
    protected abstract fun createParser(tokens: CommonTokenStream): Parser
    protected abstract fun buildResult(ctx: ParseContext, parser: Parser, warnings: List<String>): ParseResult
}

/**
 * 收集解析错误的监听器
 */
class CollectingErrorListener(
    private val tolerant: Boolean,
    private val warnings: MutableList<String>
) : BaseErrorListener() {

    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?
    ) {
        val position = ParsePosition(line, charPositionInLine)
        val message = "Syntax error at $position: $msg"
        if (tolerant) {
            warnings.add(message)
        } else {
            throw SqlSyntaxException(
                message = "SQL syntax error: $msg",
                position = position,
                offendingText = offendingSymbol?.toString()
            )
        }
    }
}
