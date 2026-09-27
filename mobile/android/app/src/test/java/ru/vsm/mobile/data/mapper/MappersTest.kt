package ru.vsm.mobile.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vsm.mobile.data.remote.dto.ChoiceOptionDto
import ru.vsm.mobile.data.remote.dto.NodeStateDto
import ru.vsm.mobile.data.remote.dto.NodeTypeDto
import ru.vsm.mobile.data.remote.dto.ProgressStatusDto
import ru.vsm.mobile.data.remote.dto.ProgressWsMessageDto
import ru.vsm.mobile.domain.model.LiveProgressEvent
import ru.vsm.mobile.domain.model.NodeType

class MappersTest {

    @Test
    fun `NodeStateDto maps type and nested choices`() {
        val dto = NodeStateDto(
            nodeId = "n1",
            code = "start",
            type = NodeTypeDto.ESCALATION,
            text = "текст",
            terminal = false,
            timerSeconds = 20,
            deadlineAt = "2026-09-26T10:00:00Z",
            terminalOutcome = null,
            outcomeSummary = null,
            choices = listOf(ChoiceOptionDto("c1", "call-chief", "Вызвать начальника поезда")),
        )

        val node = dto.toDomain()

        assertEquals(NodeType.ESCALATION, node.type)
        assertEquals(20, node.timerSeconds)
        assertEquals(1, node.choices.size)
        assertEquals("call-chief", node.choices.first().code)
    }

    @Test
    fun `ws tick message maps to Tick event`() {
        val dto = ProgressWsMessageDto(type = ProgressWsMessageDto.TYPE_TICK, progressId = "p1", secondsRemaining = 5)

        val event = dto.toDomainEvent()

        assertTrue(event is LiveProgressEvent.Tick)
        assertEquals(5, (event as LiveProgressEvent.Tick).secondsRemaining)
    }

    @Test
    fun `ws state message maps applied choice fields into LiveProgressState`() {
        val dto = ProgressWsMessageDto(
            type = ProgressWsMessageDto.TYPE_STATE,
            progressId = "p1",
            appliedChoiceId = "c1",
            appliedChoiceCode = "call-chief",
            wasTimeout = false,
            loyaltyDelta = 5,
            safetyDelta = 8,
            loyaltyScore = 5,
            safetyScore = 8,
            status = ProgressStatusDto.IN_PROGRESS,
            currentNode = null,
        )

        val event = dto.toDomainEvent()

        assertTrue(event is LiveProgressEvent.State)
        val state = (event as LiveProgressEvent.State).state
        assertEquals("call-chief", state.appliedChoiceCode)
        assertEquals(5, state.loyaltyScore)
        assertNull(state.currentNode)
    }

    @Test
    fun `ws message with unknown type maps to null`() {
        val dto = ProgressWsMessageDto(type = "unknown", progressId = "p1")

        assertNull(dto.toDomainEvent())
    }
}
