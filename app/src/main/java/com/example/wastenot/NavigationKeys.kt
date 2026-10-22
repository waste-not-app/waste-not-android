package com.example.wastenot

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable data object SessionRoot : NavKey
@Serializable data object AuthRoot : NavKey
@Serializable data class AccountRoot(val userId: String, val epoch: Long, val demo: Boolean) : NavKey
@Serializable data object Login : NavKey
@Serializable data object Register : NavKey
@Serializable data object Home : NavKey
@Serializable data object History : NavKey
@Serializable data object Guide : NavKey
@Serializable data class GuideDetail(val id: String) : NavKey
@Serializable data object Statistics : NavKey
@Serializable data object Profile : NavKey
@Serializable data object Settings : NavKey
@Serializable data class LogDetail(val id: String, val instanceId: String = UUID.randomUUID().toString()) : NavKey
@Serializable data class LogEditor(val logId: String?, val requestId: String, val guideId: String? = null) : NavKey
