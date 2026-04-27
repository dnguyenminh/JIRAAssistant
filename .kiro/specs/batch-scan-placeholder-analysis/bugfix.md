# Bugfix Requirements Document

## Introduction

Khi chạy batch scan với `batch_prompt_size > 1`, AI trả về placeholder text (ví dụ `"..."`, `"Placeholder requirement summary for {ticketId}"`) thay vì phân tích thực sự từ nội dung ticket Jira. Nguyên nhân gốc: batch prompt path không fetch nội dung ticket từ Jira/JiraContentExtractor — chỉ gửi ticket ID mà không có content, khiến AI model echo lại các giá trị mẫu trong prompt template.

Single-ticket mode (`batch_prompt_size = 1`) hoạt động đúng vì `analyzeTicket()` gọi `jiraContentExtractor.extract(ticketId)` hoặc `fetchLegacyTicketContent()` nội bộ. Batch mode bỏ qua bước này — `fetchTicketContentForBatch()` trả về `""` khi `jiraContentExtractor != null`, và `analyzeTicketBatchImpl()` không bao giờ gọi content extractor cho batch path.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN `batch_prompt_size > 1` AND `jiraContentExtractor` is available (deep analysis mode) THEN the system sends batch prompts to AI containing only ticket IDs without any ticket content, because `fetchTicketContentForBatch()` returns empty string `""` and `analyzeTicketBatchImpl()` does not call `jiraContentExtractor.extract()` for each ticket in the batch

1.2 WHEN `batch_prompt_size > 1` AND AI receives a batch prompt with empty ticket content THEN the system stores KB records with placeholder/garbage values such as `requirementSummary = "..."` or `requirementSummary = "Placeholder requirement summary for {ticketId}"`, because the AI model echoes back the example values from `BatchPromptBuilder.buildBatchPrompt()` template

1.3 WHEN `batch_prompt_size > 1` AND `jiraContentExtractor` is NOT available (legacy mode) THEN the system sends batch prompts with only summary + description (legacy fetch), but `BatchPromptBuilder.buildBatchPrompt()` includes literal `"..."` as example values in the JSON format template, which the AI model may copy verbatim instead of generating real analysis

1.4 WHEN batch analysis completes with placeholder results THEN the system reports scan as "COMPLETED" successfully and saves useless KB records, because `BatchResponseParser.parseOneResult()` accepts any non-empty string as valid `requirementSummary` without validating that it contains actual analysis content

### Expected Behavior (Correct)

2.1 WHEN `batch_prompt_size > 1` AND `jiraContentExtractor` is available THEN the system SHALL extract real ticket content via `jiraContentExtractor.extract(ticketId)` for each ticket in the batch before building the batch prompt, ensuring the AI receives actual Jira ticket content (summary, description, sub-tasks, comments, etc.)

2.2 WHEN `batch_prompt_size > 1` AND AI receives a batch prompt with real ticket content THEN the system SHALL store KB records with meaningful analysis derived from the actual ticket content, not placeholder or template values

2.3 WHEN `batch_prompt_size > 1` AND `jiraContentExtractor` is NOT available (legacy mode) THEN the system SHALL use `fetchLegacyTicketContent()` (summary + description from Jira API) for each ticket in the batch, and the batch prompt template SHALL NOT include literal placeholder values like `"..."` in the JSON example format that the AI model could copy verbatim

2.4 WHEN batch analysis produces results where `requirementSummary` is a known placeholder pattern (e.g., `"..."`, contains `"Placeholder"`, or is fewer than 10 characters) THEN the system SHALL treat those results as parse failures and fall back to single-ticket analysis for the affected tickets

### Unchanged Behavior (Regression Prevention)

3.1 WHEN `batch_prompt_size = 1` (single-ticket mode) THEN the system SHALL CONTINUE TO analyze each ticket individually via `analyzeTicket()` with full content extraction, producing real analysis results

3.2 WHEN batch analysis encounters an unparseable AI response (invalid JSON) THEN the system SHALL CONTINUE TO retry once and then fall back to single-ticket mode for the entire batch (AC 41)

3.3 WHEN batch analysis returns fewer results than expected (missing tickets) THEN the system SHALL CONTINUE TO fall back to single-ticket mode for the missing tickets only (AC 42)

3.4 WHEN a ticket already exists in KB cache and `forceReanalyze = false` THEN the system SHALL CONTINUE TO return the cached result without re-analyzing (KB-First strategy)

3.5 WHEN AI provider times out or fails during batch analysis THEN the system SHALL CONTINUE TO try the next provider in failover order, then fall back to single-ticket mode if all providers fail

### Bug Condition

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type BatchScanInput (batchPromptSize: Int, ticketIds: List<String>)
  OUTPUT: boolean
  
  // Bug triggers when batch mode is used (size > 1)
  // Content is not fetched/extracted for batch path
  RETURN X.batchPromptSize > 1
END FUNCTION
```

### Fix Checking Property

```pascal
// Property: Fix Checking — Batch prompts contain real ticket content
FOR ALL X WHERE isBugCondition(X) DO
  batchPrompt ← BatchPromptBuilder.buildBatchPrompt(ticketsWithContent)
  FOR EACH ticket IN X.ticketIds DO
    content ← extractContent(ticket)
    ASSERT content.length > 0
    ASSERT batchPrompt.contains(content)
  END FOR
  
  results ← analyzeTicketBatch(X.ticketIds)
  FOR EACH (ticketId, result) IN results DO
    ASSERT result.requirementSummary != "..."
    ASSERT NOT result.requirementSummary.contains("Placeholder")
    ASSERT result.requirementSummary.length >= 10
  END FOR
END FOR
```

### Preservation Checking Property

```pascal
// Property: Preservation Checking — Single-ticket mode unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)
  // Single-ticket analysis path remains identical
END FOR
```
