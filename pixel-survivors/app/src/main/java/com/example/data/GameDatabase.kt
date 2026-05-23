package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "highscores")
data class Highscore(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val score: Int,
    val levelReached: Int,
    val timeSurvivedSeconds: Int,
    val characterName: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "character_unlocks")
data class CharacterUnlock(
    @PrimaryKey val characterId: String,
    val isUnlocked: Boolean
)

@Entity(tableName = "powerups")
data class PowerUp(
    @PrimaryKey val statType: String, // "MIGHT", "MAX_HP", "MAGNET", "SPEED", "GREED"
    val level: Int
)

@Entity(tableName = "game_settings")
data class GameSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface GameDao {
    @Query("SELECT * FROM highscores ORDER BY score DESC LIMIT 10")
    fun getTopHighscores(): Flow<List<Highscore>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighscore(highscore: Highscore)

    @Query("SELECT * FROM character_unlocks")
    fun getCharacterUnlocksFlow(): Flow<List<CharacterUnlock>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacterUnlock(unlock: CharacterUnlock)

    @Query("SELECT * FROM powerups")
    fun getPowerUpsFlow(): Flow<List<PowerUp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPowerUp(powerUp: PowerUp)

    @Query("SELECT * FROM game_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): GameSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: GameSetting)
}

@Database(
    entities = [Highscore::class, CharacterUnlock::class, PowerUp::class, GameSetting::class],
    version = 1,
    exportSchema = false
)
abstract class GameDatabase : RoomDatabase() {
    abstract val gameDao: GameDao

    companion object {
        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getDatabase(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "pixelsurvivors_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
