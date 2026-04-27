package com.assistant.server.analysis

/**
 * JSON output schema for the Reduce phase prompt.
 * Matches the AnalysisResult format used by single-prompt flow,
 * ensuring downstream consumers (KB save, frontend) work unchanged.
 *
 * Requirements: 4.4
 */
internal val REDUCE_JSON_SCHEMA = """
{
  "requirementSummary": {
    "unified": "string — overall summary",
    "businessSummary": "string — business requirement summary",
    "asIsState": "string — current state description",
    "toBeState": "string — desired state description",
    "extractedRequirements": ["string — individual requirements"],
    "affectedModules": [
      {
        "name": "string — module name",
        "colorCategory": "PRIMARY | ACCENT | SECONDARY"
      }
    ]
  },
  "evolution": [
    {
      "version": "string — version label",
      "date": "string — ISO date",
      "description": "string — change description",
      "changeType": "ORIGIN | UPDATE | CURRENT"
    }
  ],
  "complexity": {
    "scrumPoints": 0.0,
    "description": "string — complexity rationale",
    "kbReferences": [
      {
        "ticketId": "string",
        "similarityPercent": 0.0
      }
    ]
  },
  "technicalDetails": {
    "apiSpecifications": [
      {
        "method": "string — HTTP method",
        "path": "string — endpoint path",
        "description": "string"
      }
    ],
    "databaseChanges": [
      {
        "tableName": "string",
        "operationType": "CREATE | ALTER | DROP",
        "columns": ["string"],
        "description": "string"
      }
    ],
    "externalIntegrations": [
      {
        "serviceName": "string",
        "protocol": "string",
        "endpoint": "string",
        "description": "string"
      }
    ]
  },
  "acceptanceCriteria": [
    {
      "id": "string — AC identifier",
      "description": "string",
      "testabilityAssessment": "string"
    }
  ],
  "dependencies": {
    "blockingIssues": [
      {
        "key": "string — ticket key",
        "summary": "string",
        "relationshipType": "string",
        "riskLevel": "HIGH | MEDIUM | LOW"
      }
    ],
    "relatedIssues": [
      {
        "key": "string",
        "summary": "string",
        "relationshipType": "string",
        "riskLevel": "HIGH | MEDIUM | LOW"
      }
    ],
    "externalDependencies": ["string"]
  },
  "analysisMetadata": {
    "extractionConfidence": "HIGH | MEDIUM | LOW"
  },
  "diagrams": [
    {
      "type": "flow | component | dependency | deployment | bpmn",
      "title": "string — diagram title",
      "format": "mermaid | drawio",
      "mermaidCode": "string — valid Mermaid syntax"
    }
  ]
}
""".trimIndent()
