package com.gradar.util

import android.util.Log
import com.gradar.data.EntityDatabase
import com.gradar.data.HarvestableEntity
import com.gradar.data.MobEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseSeeder(private val database: EntityDatabase) {

    companion object {
        private const val TAG = "DatabaseSeeder"

        private val SAMPLE_HARVESTABLES = listOf(
            HarvestableEntity(typeId = 201, uniqueName = "T1_WOOD", tier = 1, enchantLevel = 0, resourceType = "WOOD"),
            HarvestableEntity(typeId = 301, uniqueName = "T1_ROCK", tier = 1, enchantLevel = 0, resourceType = "ROCK"),
            HarvestableEntity(typeId = 401, uniqueName = "T1_FIBER", tier = 1, enchantLevel = 0, resourceType = "FIBER"),
            HarvestableEntity(typeId = 501, uniqueName = "T1_HIDE", tier = 1, enchantLevel = 0, resourceType = "HIDE"),
            HarvestableEntity(typeId = 101, uniqueName = "T1_ORE", tier = 1, enchantLevel = 0, resourceType = "ORE"),

            HarvestableEntity(typeId = 202, uniqueName = "T2_WOOD", tier = 2, enchantLevel = 0, resourceType = "WOOD"),
            HarvestableEntity(typeId = 302, uniqueName = "T2_ROCK", tier = 2, enchantLevel = 0, resourceType = "ROCK"),
            HarvestableEntity(typeId = 402, uniqueName = "T2_FIBER", tier = 2, enchantLevel = 0, resourceType = "FIBER"),
            HarvestableEntity(typeId = 502, uniqueName = "T2_HIDE", tier = 2, enchantLevel = 0, resourceType = "HIDE"),
            HarvestableEntity(typeId = 102, uniqueName = "T2_ORE", tier = 2, enchantLevel = 0, resourceType = "ORE"),

            HarvestableEntity(typeId = 203, uniqueName = "T3_WOOD", tier = 3, enchantLevel = 0, resourceType = "WOOD"),
            HarvestableEntity(typeId = 303, uniqueName = "T3_ROCK", tier = 3, enchantLevel = 0, resourceType = "ROCK"),
            HarvestableEntity(typeId = 403, uniqueName = "T3_FIBER", tier = 3, enchantLevel = 0, resourceType = "FIBER"),
            HarvestableEntity(typeId = 503, uniqueName = "T3_HIDE", tier = 3, enchantLevel = 0, resourceType = "HIDE"),
            HarvestableEntity(typeId = 103, uniqueName = "T3_ORE", tier = 3, enchantLevel = 0, resourceType = "ORE"),

            HarvestableEntity(typeId = 204, uniqueName = "T4_WOOD", tier = 4, enchantLevel = 0, resourceType = "WOOD"),
            HarvestableEntity(typeId = 2041, uniqueName = "T4_WOOD_LEVEL1", tier = 4, enchantLevel = 1, resourceType = "WOOD"),
            HarvestableEntity(typeId = 2042, uniqueName = "T4_WOOD_LEVEL2", tier = 4, enchantLevel = 2, resourceType = "WOOD"),
            HarvestableEntity(typeId = 2043, uniqueName = "T4_WOOD_LEVEL3", tier = 4, enchantLevel = 3, resourceType = "WOOD"),

            HarvestableEntity(typeId = 304, uniqueName = "T4_ROCK", tier = 4, enchantLevel = 0, resourceType = "ROCK"),
            HarvestableEntity(typeId = 404, uniqueName = "T4_FIBER", tier = 4, enchantLevel = 0, resourceType = "FIBER"),
            HarvestableEntity(typeId = 504, uniqueName = "T4_HIDE", tier = 4, enchantLevel = 0, resourceType = "HIDE"),
            HarvestableEntity(typeId = 104, uniqueName = "T4_ORE", tier = 4, enchantLevel = 0, resourceType = "ORE"),

            HarvestableEntity(typeId = 205, uniqueName = "T5_WOOD", tier = 5, enchantLevel = 0, resourceType = "WOOD"),
            HarvestableEntity(typeId = 305, uniqueName = "T5_ROCK", tier = 5, enchantLevel = 0, resourceType = "ROCK"),
            HarvestableEntity(typeId = 405, uniqueName = "T5_FIBER", tier = 5, enchantLevel = 0, resourceType = "FIBER"),
            HarvestableEntity(typeId = 505, uniqueName = "T5_HIDE", tier = 5, enchantLevel = 0, resourceType = "HIDE"),
            HarvestableEntity(typeId = 105, uniqueName = "T5_ORE", tier = 5, enchantLevel = 0, resourceType = "ORE"),

            HarvestableEntity(typeId = 206, uniqueName = "T6_WOOD", tier = 6, enchantLevel = 0, resourceType = "WOOD"),
            HarvestableEntity(typeId = 306, uniqueName = "T6_ROCK", tier = 6, enchantLevel = 0, resourceType = "ROCK"),
            HarvestableEntity(typeId = 406, uniqueName = "T6_FIBER", tier = 6, enchantLevel = 0, resourceType = "FIBER"),
            HarvestableEntity(typeId = 506, uniqueName = "T6_HIDE", tier = 6, enchantLevel = 0, resourceType = "HIDE"),
            HarvestableEntity(typeId = 106, uniqueName = "T6_ORE", tier = 6, enchantLevel = 0, resourceType = "ORE"),

            HarvestableEntity(typeId = 207, uniqueName = "T7_WOOD", tier = 7, enchantLevel = 0, resourceType = "WOOD"),
            HarvestableEntity(typeId = 307, uniqueName = "T7_ROCK", tier = 7, enchantLevel = 0, resourceType = "ROCK"),
            HarvestableEntity(typeId = 407, uniqueName = "T7_FIBER", tier = 7, enchantLevel = 0, resourceType = "FIBER"),
            HarvestableEntity(typeId = 507, uniqueName = "T7_HIDE", tier = 7, enchantLevel = 0, resourceType = "HIDE"),
            HarvestableEntity(typeId = 107, uniqueName = "T7_ORE", tier = 7, enchantLevel = 0, resourceType = "ORE"),

            HarvestableEntity(typeId = 208, uniqueName = "T8_WOOD", tier = 8, enchantLevel = 0, resourceType = "WOOD"),
            HarvestableEntity(typeId = 308, uniqueName = "T8_ROCK", tier = 8, enchantLevel = 0, resourceType = "ROCK"),
            HarvestableEntity(typeId = 408, uniqueName = "T8_FIBER", tier = 8, enchantLevel = 0, resourceType = "FIBER"),
            HarvestableEntity(typeId = 508, uniqueName = "T8_HIDE", tier = 8, enchantLevel = 0, resourceType = "HIDE"),
            HarvestableEntity(typeId = 108, uniqueName = "T8_ORE", tier = 8, enchantLevel = 0, resourceType = "ORE")
        )

        private val SAMPLE_MOBS = listOf(
            MobEntity(typeId = 1001, uniqueName = "T1_MOB_HERETIC", tier = 1, mobCategory = "standard"),
            MobEntity(typeId = 1002, uniqueName = "T2_MOB_HERETIC_SOLDIER", tier = 2, mobCategory = "standard"),
            MobEntity(typeId = 1003, uniqueName = "T3_MOB_HERETIC_CHAMPION", tier = 3, mobCategory = "champion"),
            MobEntity(typeId = 2001, uniqueName = "T4_MOB_UNDEAD_SKELLIE", tier = 4, mobCategory = "standard"),
            MobEntity(typeId = 2002, uniqueName = "T5_MOB_UNDEAD_CHAMPION", tier = 5, mobCategory = "champion"),
            MobEntity(typeId = 3001, uniqueName = "T6_MOB_DEMON", tier = 6, mobCategory = "standard"),
            MobEntity(typeId = 3002, uniqueName = "T7_MOB_DEMON_CHAMPION", tier = 7, mobCategory = "champion"),
            MobEntity(typeId = 5001, uniqueName = "T5_MOB_MINIBOSS", tier = 5, mobCategory = "miniboss"),
            MobEntity(typeId = 5002, uniqueName = "T6_MOB_MINIBOSS", tier = 6, mobCategory = "miniboss"),
            MobEntity(typeId = 6001, uniqueName = "T7_MOB_BOSS", tier = 7, mobCategory = "boss"),
            MobEntity(typeId = 6002, uniqueName = "T8_MOB_BOSS", tier = 8, mobCategory = "boss")
        )
    }

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val harvestableCount = database.harvestableDao().count()
            val mobCount = database.mobDao().count()

            if (harvestableCount == 0) {
                Log.i(TAG, "Seeding harvestables database...")
                database.harvestableDao().insertAll(SAMPLE_HARVESTABLES)
                Log.i(TAG, "Seeded ${SAMPLE_HARVESTABLES.size} harvestables")
            }

            if (mobCount == 0) {
                Log.i(TAG, "Seeding mobs database...")
                database.mobDao().insertAll(SAMPLE_MOBS)
                Log.i(TAG, "Seeded ${SAMPLE_MOBS.size} mobs")
            }

            Log.i(TAG, "Database seeding complete. Harvestables: ${database.harvestableDao().count()}, Mobs: ${database.mobDao().count()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seed database", e)
        }
    }
}
