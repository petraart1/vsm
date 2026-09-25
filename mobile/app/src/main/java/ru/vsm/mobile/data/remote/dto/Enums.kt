package ru.vsm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/** Провод-формат enum'ов backend (сериализуются Jackson по имени константы) — 1:1 с `ru.vsm.backend.scenario.domain.*`. */
@Serializable
enum class NodeTypeDto { DIALOGUE, ESCALATION, TERMINAL }

@Serializable
enum class ScenarioOutcomeDto { SUCCESS, PARTIAL, FAILURE }

@Serializable
enum class ProgressStatusDto { IN_PROGRESS, COMPLETED, ABANDONED }

@Serializable
enum class RoleStepDto { ACKNOWLEDGE, RULE, SOLUTION, REASSURE }

@Serializable
enum class RecommendationReasonDto { NOT_PLAYED, FAILED, PARTIAL }
