package com.example.engine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.GameAudioPlayer
import com.example.data.CharacterUnlock
import com.example.data.GameDatabase
import com.example.data.GameRepository
import com.example.data.Highscore
import com.example.data.PowerUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    object MainMenu : Screen()
    object CharacterSelect : Screen()
    object Shop : Screen()
    object Highscores : Screen()
    object Play : Screen()
    data class GameOver(val score: Int, val level: Int, val timeSeconds: Int, val goldCollected: Int) : Screen()
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val database = GameDatabase.getDatabase(application)
    private val repository = GameRepository(database.gameDao)
    val soundPlayer = GameAudioPlayer()

    // Screen navigation
    private val _currentScreen = MutableStateFlow<Screen>(Screen.MainMenu)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Core reactive streams from Database
    val topHighscores: StateFlow<List<Highscore>> = repository.topHighscores
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val characterUnlocks: StateFlow<List<CharacterUnlock>> = repository.characterUnlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val powerUps: StateFlow<List<PowerUp>> = repository.powerUps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _totalGold = MutableStateFlow<Int>(0)
    val totalGold: StateFlow<Int> = _totalGold.asStateFlow()

    // Game Runtime Engine state
    private val _activeEngine = MutableStateFlow<GameEngine?>(null)
    val activeEngine: StateFlow<GameEngine?> = _activeEngine.asStateFlow()

    private var gameLoopJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initializeDefaults()
            refreshGold()
        }
    }

    private suspend fun refreshGold() {
        val g = repository.getGold()
        _totalGold.value = g
    }

    fun navigateTo(screen: Screen) {
        // Clear active loop if exiting play screen
        if (screen != Screen.Play && gameLoopJob != null) {
            stopGameLoop()
        }
        _currentScreen.value = screen
    }

    fun selectCharacterAndStart(character: CharacterConfig) {
        viewModelScope.launch {
            val statsMap = mutableMapOf<String, Int>()
            repository.powerUps.stateIn(this).value.forEach {
                statsMap[it.statType] = it.level
            }

            val engine = GameEngine(
                selectedCharacter = character,
                permaPowerUps = statsMap,
                soundPlayer = soundPlayer,
                onGameOver = { score, lvl, duration, gold ->
                    saveRunAndStop(score, lvl, duration, gold, character.name)
                }
            )

            _activeEngine.value = engine
            _currentScreen.value = Screen.Play

            // Fire running loop
            startGameLoop(engine)
        }
    }

    private fun startGameLoop(engine: GameEngine) {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch(Dispatchers.Default) {
            var lastTickTime = System.currentTimeMillis()
            while (isActive && _currentScreen.value == Screen.Play) {
                val now = System.currentTimeMillis()
                engine.step(now)
                // Small sleep to maintain steady state refresh without CPU starving
                delay(15L)
            }
        }
    }

    private fun stopGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
        _activeEngine.value = null
    }

    private fun saveRunAndStop(score: Int, lvl: Int, duration: Int, gold: Int, charName: String) {
        viewModelScope.launch {
            // Persist run gold and scores
            repository.addGold(gold)
            repository.saveHighscore(score, lvl, duration, charName)
            refreshGold()

            withContext(Dispatchers.Main) {
                stopGameLoop()
                _currentScreen.value = Screen.GameOver(score, lvl, duration, gold)
            }
        }
    }

    // Shop actions
    fun purchaseCharacter(config: CharacterConfig) {
        viewModelScope.launch {
            val success = repository.unlockCharacter(config.id, config.unlockCost)
            if (success) {
                refreshGold()
                soundPlayer.playCoin()
            } else {
                soundPlayer.playHurt() // warning error buzzer
            }
        }
    }

    fun upgradeStat(statType: String, cost: Int) {
        viewModelScope.launch {
            val success = repository.upgradePowerUp(statType, cost, maxLevel = 5)
            if (success) {
                refreshGold()
                soundPlayer.playCoin()
            } else {
                soundPlayer.playHurt() // buzzer
            }
        }
    }
}
