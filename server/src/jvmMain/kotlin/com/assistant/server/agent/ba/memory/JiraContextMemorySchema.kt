package com.assistant.server.agent.ba.memory

import com.assistant.agent.memory.SlotSchema
import com.assistant.agent.memory.SlotType
import com.assistant.agent.memory.StructuredMemory

/**
 * Defines the JiraContextMemory schema — 9 typed slots
 * for organizing Jira ticket data collected by the BA Agent.
 *
 * Each slot has a type (STRING, LIST, MAP) and a max capacity.
 * STRING slots measure capacity in total characters.
 * LIST/MAP slots measure capacity in number of entries.
 */
object JiraContextMemorySchema {

    val SLOTS: List<SlotSchema> = listOf(
        SlotSchema("summary", SlotType.STRING, maxSize = 10_000),
        SlotSchema("description", SlotType.STRING, maxSize = 10_000),
        SlotSchema("comments", SlotType.LIST, maxSize = 50),
        SlotSchema("attachmentsData", SlotType.LIST, maxSize = 30),
        SlotSchema("linkedTickets", SlotType.MAP, maxSize = 20),
        SlotSchema("businessGoals", SlotType.STRING, maxSize = 10_000),
        SlotSchema("kbRecords", SlotType.MAP, maxSize = 20),
        SlotSchema("technicalDetails", SlotType.STRING, maxSize = 10_000),
        SlotSchema("acceptanceCriteria", SlotType.LIST, maxSize = 50),
        SlotSchema("ticketClassifications", SlotType.MAP, maxSize = 20)
    )

    /** Factory method returning a fresh StructuredMemory with the Jira schema. */
    fun createMemory(): StructuredMemory = StructuredMemory(SLOTS)
}
