package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class GameRepository(private val gameDao: GameDao) {
    val topHighscores: Flow<List<Highscore>> = gameDao.getTopHighscores()
    val characterUnlocks: Flow<List<CharacterUnlock>> = gameDao.getCharacterUnlocksFlow()
    val powerUps: Flow<List<PowerUp>> = gameDao.getPowerUpsFlow()

    suspend fun saveHighscore(score: Int, levelReached: Int, timeSurvivedSeconds: Int, characterName: String) {
        val highscore = Highscore(
            score = score,
            levelReached = levelReached,
            timeSurvivedSeconds = timeSurvivedSeconds,
            characterName = characterName
        )
        gameDao.insertHighscore(highscore)
    }

    suspend fun getGold(): Int {
        val setting = gameDao.getSetting("TOTAL_GOLD")
        return setting?.value?.toIntOrNull() ?: 0
    }

    suspend fun saveGold(gold: Int) {
        gameDao.insertSetting(GameSetting("TOTAL_GOLD", gold.toString()))
    }

    suspend fun addGold(amount: Int) {
        val currentGold = getGold()
        saveGold(currentGold + amount)
    }

    suspend fun unlockCharacter(characterId: String, cost: Int): Boolean {
        val currentGold = getGold()
        if (currentGold >= cost) {
            saveGold(currentGold - cost)
            gameDao.insertCharacterUnlock(CharacterUnlock(characterId, true))
            return true
        }
        return false
    }

    suspend fun upgradePowerUp(statType: String, cost: Int, maxLevel: Int = 5): Boolean {
        val currentGold = getGold()
        if (currentGold >= cost) {
            val powerUpsList = powerUps.first()
            val currentPowerUp = powerUpsList.find { it.statType == statType }
            val currentLevel = currentPowerUp?.level ?: 0
            if (currentLevel < maxLevel) {
                saveGold(currentGold - cost)
                gameDao.insertPowerUp(PowerUp(statType, currentLevel + 1))
                return true
            }
        }
        return false
    }

    suspend fun initializeDefaults() {
        // Initialize default character unlocks
        val unlocks = characterUnlocks.first()
        if (unlocks.isEmpty()) {
            gameDao.insertCharacterUnlock(CharacterUnlock("antonio", true))
            gameDao.insertCharacterUnlock(CharacterUnlock("imelda", true))
            gameDao.insertCharacterUnlock(CharacterUnlock("cavallo", false))
            gameDao.insertCharacterUnlock(CharacterUnlock("poe", false))
        }

        // Initialize default stats if not existing
        val statsList = powerUps.first()
        if (statsList.isEmpty()) {
            gameDao.insertPowerUp(PowerUp("MIGHT", 0))
            gameDao.insertPowerUp(PowerUp("MAX_HP", 0))
            gameDao.insertPowerUp(PowerUp("MAGNET", 0))
            gameDao.insertPowerUp(PowerUp("SPEED", 0))
            gameDao.insertPowerUp(PowerUp("GREED", 0))
        }

        // Initialize default total gold if not set
        val setting = gameDao.getSetting("TOTAL_GOLD")
        if (setting == null) {
            // start the player with 150 gold so they can immediately buy something or feel rich!
            saveGold(150)
        }
    }
}
