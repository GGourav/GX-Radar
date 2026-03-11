package com.gradar.data

import androidx.room.*
import com.gradar.model.EntityType
import com.gradar.model.MobCategory
import com.gradar.model.ResourceType

/**
 * Room database for entity type mappings.
 */
@Database(
    entities = [HarvestableEntity::class, MobEntity::class],
    version = 1,
    exportSchema = true
)
abstract class EntityDatabase : RoomDatabase() {
    abstract fun harvestableDao(): HarvestableDao
    abstract fun mobDao(): MobDao
}

@Entity(tableName = "harvestables", indices = [Index("typeId", unique = true)])
data class HarvestableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val typeId: Int,
    val uniqueName: String,
    val tier: Int,
    val enchantLevel: Int,
    val resourceType: String
)

@Entity(tableName = "mobs", indices = [Index("typeId", unique = true)])
data class MobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val typeId: Int,
    val uniqueName: String,
    val tier: Int,
    val mobCategory: String
)

@Dao
interface HarvestableDao {
    @Query("SELECT * FROM harvestables WHERE typeId = :typeId LIMIT 1")
    suspend fun findByTypeId(typeId: Int): HarvestableEntity?

    @Query("SELECT * FROM harvestables WHERE uniqueName LIKE :pattern")
    suspend fun findByUniqueName(pattern: String): List<HarvestableEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HarvestableEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HarvestableEntity>)

    @Query("SELECT COUNT(*) FROM harvestables")
    suspend fun count(): Int
}

@Dao
interface MobDao {
    @Query("SELECT * FROM mobs WHERE typeId = :typeId LIMIT 1")
    suspend fun findByTypeId(typeId: Int): MobEntity?

    @Query("SELECT * FROM mobs WHERE uniqueName LIKE :pattern")
    suspend fun findByUniqueName(pattern: String): List<MobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MobEntity>)

    @Query("SELECT COUNT(*) FROM mobs")
    suspend fun count(): Int
}

class EntityClassifier(private val database: EntityDatabase) {

    suspend fun classifyEntity(
        typeId: Int,
        uniqueName: String?,
        factionFlags: Byte
    ): EntityInfo? {
        if (typeId == -1) {
            return EntityInfo(
                entityType = when (factionFlags.toInt() and 0xFF) {
                    0 -> EntityType.PLAYER_PASSIVE
                    1 -> EntityType.PLAYER_FACTION
                    255 -> EntityType.PLAYER_HOSTILE
                    else -> EntityType.PLAYER_PASSIVE
                },
                tier = 0,
                enchantLevel = 0,
                displayName = uniqueName ?: "Player"
            )
        }

        val harvestable = database.harvestableDao().findByTypeId(typeId)
        if (harvestable != null) {
            return EntityInfo(
                entityType = EntityType.RESOURCE,
                tier = harvestable.tier,
                enchantLevel = harvestable.enchantLevel,
                displayName = harvestable.uniqueName,
                resourceType = ResourceType.fromString(harvestable.resourceType)
            )
        }

        val mob = database.mobDao().findByTypeId(typeId)
        if (mob != null) {
            val category = MobCategory.fromString(mob.mobCategory)
            return EntityInfo(
                entityType = when (category) {
                    MobCategory.CHAMPION -> EntityType.MOB_ENCHANTED
                    MobCategory.BOSS, MobCategory.MINIBOSS -> EntityType.MOB_BOSS
                    MobCategory.ENVIRONMENT -> null
                    else -> EntityType.MOB_NORMAL
                },
                tier = mob.tier,
                enchantLevel = 0,
                displayName = mob.uniqueName
            )
        }

        if (uniqueName != null) {
            return classifyByUniqueName(uniqueName)
        }

        return EntityInfo(
            entityType = EntityType.UNKNOWN,
            tier = 0,
            enchantLevel = 0,
            displayName = "Unknown ($typeId)"
        )
    }

    private fun classifyByUniqueName(name: String): EntityInfo? {
        val resourcePattern = Regex("""T(\d)_(WOOD|ROCK|FIBER|HIDE|ORE)(?:_LEVEL(\d))?""", RegexOption.IGNORE_CASE)
        val resourceMatch = resourcePattern.find(name)

        if (resourceMatch != null) {
            val tier = resourceMatch.groupValues[1].toIntOrNull() ?: return null
            val type = resourceMatch.groupValues[2].uppercase()
            val enchant = resourceMatch.groupValues[3].toIntOrNull() ?: 0

            return EntityInfo(
                entityType = EntityType.RESOURCE,
                tier = tier,
                enchantLevel = enchant,
                displayName = name,
                resourceType = ResourceType.valueOf(type)
            )
        }

        val mobPattern = Regex("""T(\d)_MOB_(\w+)""", RegexOption.IGNORE_CASE)
        val mobMatch = mobPattern.find(name)

        if (mobMatch != null) {
            val tier = mobMatch.groupValues[1].toIntOrNull() ?: return null
            val category = mobMatch.groupValues[2].uppercase()

            return EntityInfo(
                entityType = when {
                    category.contains("CHAMPION") -> EntityType.MOB_ENCHANTED
                    category.contains("BOSS") -> EntityType.MOB_BOSS
                    else -> EntityType.MOB_NORMAL
                },
                tier = tier,
                enchantLevel = 0,
                displayName = name
            )
        }

        return EntityInfo(
            entityType = EntityType.UNKNOWN,
            tier = 0,
            enchantLevel = 0,
            displayName = name
        )
    }
}

data class EntityInfo(
    val entityType: EntityType?,
    val tier: Int,
    val enchantLevel: Int,
    val displayName: String,
    val resourceType: ResourceType = ResourceType.UNKNOWN
)
