package com.assistant.ai.deepanalysis

/**
 * Regex patterns for section classification in ticket descriptions.
 * Requirements: 17.1-17.6
 */
internal object SectionPatterns {

    // Req 17.1 — As-Is / To-Be heading patterns
    val AS_IS_HEADING = Regex(
        """(?i)^#+\s*(as[- ]?is|current\s+state|hiện\s+trạng)\s*:?\s*$""",
        RegexOption.MULTILINE
    )
    val TO_BE_HEADING = Regex(
        """(?i)^#+\s*(to[- ]?be|expected\s+state|trạng\s+thái\s+mong\s+muốn)\s*:?\s*$""",
        RegexOption.MULTILINE
    )

    // Req 17.2 — API specification patterns
    val HTTP_METHOD_LINE = Regex(
        """(?i)(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+(/\S+)""",
        RegexOption.MULTILINE
    )
    val API_HEADING = Regex(
        """(?i)^#+\s*(api|endpoint|rest\s+api)\s*:?\s*$""",
        RegexOption.MULTILINE
    )

    // Req 17.3 — Database change patterns
    val SQL_STATEMENT = Regex(
        """(?i)(CREATE|ALTER|DROP)\s+TABLE\s+(\S+)""",
        RegexOption.MULTILINE
    )
    val DB_HEADING = Regex(
        """(?i)^#+\s*(database|db\s+change|migration|schema)\s*:?\s*$""",
        RegexOption.MULTILINE
    )
    val COLUMN_DEF = Regex(
        """(?i)(\w+)\s+(VARCHAR|INT|INTEGER|BIGINT|TEXT|BOOLEAN|DATE|TIMESTAMP|DECIMAL|FLOAT|DOUBLE|UUID|SERIAL|JSONB?)""",
        RegexOption.MULTILINE
    )

    // Req 17.4 — External dependency patterns
    val INTEGRATION_HEADING = Regex(
        """(?i)^#+\s*(integration|external\s+(service|dependency|system)|tích\s+hợp)\s*:?\s*$""",
        RegexOption.MULTILINE
    )
    val PROTOCOL_PATTERN = Regex(
        """(?i)(https?|grpc|graphql|soap|websocket|amqp|mqtt)://(\S+)""",
        RegexOption.MULTILINE
    )

    // Req 17.5 — Acceptance criteria patterns
    val AC_HEADING = Regex(
        """(?i)^#+\s*(acceptance\s+criteria|definition\s+of\s+done|tiêu\s+chí\s+chấp\s+nhận|AC)\s*:?\s*$""",
        RegexOption.MULTILINE
    )
    val AC_ITEM = Regex(
        """(?i)^\s*[-*•]\s*(GIVEN|WHEN|THEN|AC\s*\d|Scenario)\b(.*)""",
        RegexOption.MULTILINE
    )
}
