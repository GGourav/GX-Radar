package com.gradar.model

data class RadarEntity(
    val entityId: Int,
    val typeId: Int,
    val uniqueName: String?,
    val entityName: String?,
    val entityType: EntityType,
    val tier: Int,
    val enchantLevel: Int,
    val posX: Float,
    val posZ: Float,
    val factionFlags: Byte = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun distanceFrom(x: Float, z: Float): Float {
        val dx = posX - x
        val dz = posZ - z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }

    fun isExpired(ttlMs: Long = 300_000L): Boolean {
        return System.currentTimeMillis() - timestamp > ttlMs
    }
}

enum class EntityType {
    RESOURCE,
    MOB_NORMAL,
    MOB_ENCHANTED,
    MOB_BOSS,
    PLAYER_PASSIVE,
    PLAYER_FACTION,
    PLAYER_HOSTILE,
    MIST_WISP,
    UNKNOWN;

    fun getDisplayColor(): Int = when (this) {
        RESOURCE -> 0xFF2979FF.toInt()
        MOB_NORMAL -> 0xFF00E676.toInt()
        MOB_ENCHANTED -> 0xFFD500F9.toInt()
        MOB_BOSS -> 0xFFFF6D00.toInt()
        PLAYER_PASSIVE -> 0xFFFFFFFF.toInt()
        PLAYER_FACTION -> 0xFFFF6D00.toInt()
        PLAYER_HOSTILE -> 0xFFFF1744.toInt()
        MIST_WISP -> 0xFFE0E0E0.toInt()
        UNKNOWN -> 0xFF9E9E9E.toInt()
    }
}

enum class ResourceType {
    WOOD,
    ROCK,
    FIBER,
    HIDE,
    ORE,
    UNKNOWN;

    companion object {
        fun fromString(name: String): ResourceType = when {
            name.contains("WOOD", ignoreCase = true) -> WOOD
            name.contains("ROCK", ignoreCase = true) -> ROCK
            name.contains("FIBER", ignoreCase = true) -> FIBER
            name.contains("HIDE", ignoreCase = true) -> HIDE
            name.contains("ORE", ignoreCase = true) -> ORE
            else -> UNKNOWN
        }
    }
}

enum class MobCategory {
    STANDARD,
    CHAMPION,
    MINIBOSS,
    BOSS,
    ENVIRONMENT,
    UNKNOWN;

    companion object {
        fun fromString(category: String?): MobCategory = when (category?.lowercase()) {
            "standard", "trash", "critter", "summon" -> STANDARD
            "champion" -> CHAMPION
            "miniboss" -> MINIBOSS
            "boss" -> BOSS
            "environment", "harmless", "vanity" -> ENVIRONMENT
            else -> UNKNOWN
        }
    }
}

data class FilterConfig(
    val radarSize: Int = 300,
    val zoom: Float = 1.0f,
    val enabledTiers: Set<Int> = (1..8).toSet(),
    val enabledResources: Set<ResourceType> = ResourceType.values().toSet(),
    val enabledEnchants: Set<Int> = (0..4).toSet(),
    val showMobs: Boolean = true,
    val showEnchantedMobs: Boolean = true,
    val showBosses: Boolean = true,
    val showPassivePlayers: Boolean = true,
    val showHostilePlayers: Boolean = true
) {
    fun matches(entity: RadarEntity): Boolean {
        if (entity.tier !in enabledTiers) return false
        if (entity.enchantLevel !in enabledEnchants) return false

        return when (entity.entityType) {
            EntityType.RESOURCE -> {
                val resourceType = ResourceType.fromString(entity.uniqueName ?: "")
                resourceType in enabledResources
            }
            EntityType.MOB_NORMAL -> showMobs
            EntityType.MOB_ENCHANTED -> showEnchantedMobs
            EntityType.MOB_BOSS -> showBosses
            EntityType.PLAYER_PASSIVE -> showPassivePlayers
            EntityType.PLAYER_FACTION -> showPassivePlayers || showHostilePlayers
            EntityType.PLAYER_HOSTILE -> showHostilePlayers
            EntityType.MIST_WISP -> true
            EntityType.UNKNOWN -> true
        }
    }
}

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Connected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

data class PacketStats(
    val totalPackets: Long = 0,
    val gamePackets: Long = 0,
    val eventsProcessed: Long = 0,
    val entitiesSpawned: Long = 0,
    val entitiesDespawned: Long = 0,
    val unknownEntities: Long = 0
)
