package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.engine.*
import com.example.data.*
import kotlin.math.*

@Composable
fun PixelSurvivorsConsole(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val totalGold by viewModel.totalGold.collectAsState()
    val topScores by viewModel.topHighscores.collectAsState()
    val unlocks by viewModel.characterUnlocks.collectAsState()
    val statsList by viewModel.powerUps.collectAsState()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C090A)), // very dark obsidian black
        color = Color(0xFF0C090A)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                is Screen.MainMenu -> {
                    MainMenuScreen(
                        totalGold = totalGold,
                        onPlayClick = { viewModel.navigateTo(Screen.CharacterSelect) },
                        onShopClick = { viewModel.navigateTo(Screen.Shop) },
                        onScoresClick = { viewModel.navigateTo(Screen.Highscores) }
                    )
                }
                is Screen.CharacterSelect -> {
                    CharacterSelectScreen(
                        totalGold = totalGold,
                        unlocks = unlocks,
                        onCharacterSelected = { viewModel.selectCharacterAndStart(it) },
                        onUnlockPurchase = { viewModel.purchaseCharacter(it) },
                        onBackClick = { viewModel.navigateTo(Screen.MainMenu) }
                    )
                }
                is Screen.Shop -> {
                    ShopScreen(
                        totalGold = totalGold,
                        powerups = statsList,
                        onUpgradeClick = { stat, cost -> viewModel.upgradeStat(stat, cost) },
                        onBackClick = { viewModel.navigateTo(Screen.MainMenu) }
                    )
                }
                is Screen.Highscores -> {
                    HighscoresScreen(
                        scores = topScores,
                        onBackClick = { viewModel.navigateTo(Screen.MainMenu) }
                    )
                }
                is Screen.Play -> {
                    val activeEngine by viewModel.activeEngine.collectAsState()
                    activeEngine?.let { engine ->
                        PlayScreen(
                            engine = engine,
                            onPauseToggle = { engine.isPaused = !engine.isPaused },
                            onExitGame = { viewModel.navigateTo(Screen.MainMenu) }
                        )
                    } ?: Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.Red)
                    }
                }
                is Screen.GameOver -> {
                    GameOverScreen(
                        score = screen.score,
                        level = screen.level,
                        timeSeconds = screen.timeSeconds,
                        goldCollected = screen.goldCollected,
                        onReturnClick = { viewModel.navigateTo(Screen.MainMenu) }
                    )
                }
            }
        }
    }
}

// 1. MAIN MENU SCREEN
@Composable
fun MainMenuScreen(
    totalGold: Int,
    onPlayClick: () -> Unit,
    onShopClick: () -> Unit,
    onScoresClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bannerAnimation")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1C1B1F), Color(0xFF0F0D13)),
                    radius = 1200f
                )
            )
    ) {
        // Subtle decorative grid cells in the background
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.08f)) {
            val sizePx = 24.dp.toPx()
            var x = 0f
            while (x < size.width) {
                var y = 0f
                while (y < size.height) {
                    drawCircle(Color(0xFF49454F), radius = 2f, center = Offset(x, y))
                    y += sizePx
                }
                x += sizePx
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Gold Indicator Capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF49454F), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Gold USD",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${totalGold} GP",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Title Column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1B1F), RoundedCornerShape(16.dp))
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PIXEL",
                            color = Color(0xFFE6E1E5),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "SURVIVORS",
                            color = Color(0xFFD0BCFF),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SWARMING ULTRA ACTION REAP",
                    color = Color(0xFFD0BCFF).copy(alpha = pulseAlpha),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Interactive Buttons with Sleek Interface Theme
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Primary Action Button (MD3 Fab layout)
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD0BCFF),
                        contentColor = Color(0xFF381E72)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(56.dp)
                        .testTag("survive_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF381E72),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ENTER SURVIVAL NIGHT",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Secondary Action: permanent shop
                Button(
                    onClick = onShopClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2930)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(50.dp)
                        .border(1.5.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
                        .testTag("powerups_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PERMANENT SHOP",
                        color = Color(0xFFE6E1E5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Third: highscores
                Button(
                    onClick = onScoresClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1B1F)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(50.dp)
                        .border(1.dp, Color(0xFF49454F).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .testTag("highscores_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.List,
                        contentDescription = null,
                        tint = Color(0xFFE6E1E5).copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GRAVEYARD LEGENDS",
                        color = Color(0xFFE6E1E5).copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// 2. CHARACTER SELECT SCREEN
@Composable
fun CharacterSelectScreen(
    totalGold: Int,
    unlocks: List<CharacterUnlock>,
    onCharacterSelected: (CharacterConfig) -> Unit,
    onUnlockPurchase: (CharacterConfig) -> Unit,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0D13))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFE6E1E5))
                }
                Text(
                    text = "SELECT SURVIVOR",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                // Gold Indicator Capsule
                Row(
                    modifier = Modifier
                        .background(Color(0xFF49454F), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${totalGold} GP",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp)

            Spacer(modifier = Modifier.height(16.dp))

            // Grid/List of characters
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(CharacterConfig.ALL) { config ->
                    val isUnlocked = unlocks.firstOrNull { it.characterId == config.id }?.isUnlocked ?: (config.unlockCost == 0)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isUnlocked) 1.5.dp else 1.dp,
                                color = if (isUnlocked) Color(0xFFD0BCFF) else Color(0xFF49454F),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(config.normalColor, CircleShape)
                                            .border(2.dp, config.neonColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = config.name.uppercase(),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                if (!isUnlocked) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color(0xFF3B1F22), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFFFB4AB).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .clickable { onUnlockPurchase(config) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Lock,
                                            contentDescription = "locked",
                                            tint = Color(0xFFFFB4AB),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "UNLOCK ${config.unlockCost} GP",
                                            color = Color(0xFFFFB4AB),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "READY",
                                        color = Color(0xFFD0BCFF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = config.description,
                                color = Color(0xFFE6E1E5).copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontFamily = FontFamily.SansSerif
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Character Stats info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "HP: ${config.baseMaxHp.toInt()}",
                                    color = Color(0xFFD0BCFF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "SPEED: +${((config.baseSpeed - 3.5f) * 20).toInt()}%",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "MIGHT: +${((config.baseDamageMultiplier - 1.0f) * 100).toInt()}%",
                                    color = Color(0xFFFFB4AB),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Select click trigger
                            if (isUnlocked) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { onCharacterSelected(config) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD0BCFF),
                                        contentColor = Color(0xFF381E72)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "DEPLOY SURVIVOR",
                                        color = Color(0xFF381E72),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 3. STAT SHOP SCREEN
@Composable
fun ShopScreen(
    totalGold: Int,
    powerups: List<PowerUp>,
    onUpgradeClick: (statType: String, cost: Int) -> Unit,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0D13))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFE6E1E5))
                }
                Text(
                    text = "PERMANENT SHOP",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                // Gold Indicator Capsule consistent styling
                Row(
                    modifier = Modifier
                        .background(Color(0xFF49454F), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${totalGold} GP",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp)

            Spacer(modifier = Modifier.height(16.dp))

            // Power-up categories list
            val statUpgradesList = listOf(
                Triple("MIGHT", "Vampiric Might", "Multiplies all outgoing attack damage by +15% per tier."),
                Triple("MAX_HP", "Dracul Armor", "Grants +15 maximum Health points, scaling active level capacity."),
                Triple("MAGNET", "Astral Attraction", "Increases experience collection pull radius by +25%."),
                Triple("SPEED", "Shadow Haste", "Increases general player running velocity speed by +8%."),
                Triple("GREED", "Demon Greed", "Multiplies all run gold collections by +15% coin yield.")
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(statUpgradesList) { (statType, name, desc) ->
                    val currentLvl = powerups.firstOrNull { it.statType == statType }?.level ?: 0
                    val cost = (currentLvl * 250) + 250
                    val isMax = currentLvl >= 5
                    val canAfford = totalGold >= cost

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name.uppercase(),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )

                                // Status indicators dots (lavender purple for upgraded, dark slate for remaining)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (i in 1..5) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (i <= currentLvl) Color(0xFFD0BCFF) else Color(0xFF49454F)
                                                )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = desc,
                                color = Color(0xFFE6E1E5).copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                fontFamily = FontFamily.SansSerif
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LEVEL: $currentLvl / 5",
                                    color = Color(0xFFD0BCFF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )

                                if (isMax) {
                                    Text(
                                        text = "MAXED OUT TIER",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp
                                    )
                                } else {
                                    Button(
                                        onClick = { onUpgradeClick(statType, cost) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (canAfford) Color(0xFFD0BCFF) else Color(0xFF2B2930),
                                            contentColor = if (canAfford) Color(0xFF381E72) else Color(0xFFE6E1E5).copy(alpha = 0.4f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(38.dp)
                                            .border(
                                                width = 1.dp,
                                                color = if (canAfford) Color.Transparent else Color(0xFF49454F),
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Buy",
                                            tint = if (canAfford) Color(0xFF381E72) else Color(0xFFE6E1E5).copy(alpha = 0.4f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${cost} GP",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 4. GRAVEYARD LEGENDS RECORD SCOREBOARD (HIGHSCORES)
@Composable
fun HighscoresScreen(
    scores: List<Highscore>,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0D13))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFE6E1E5))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "GRAVEYARD LEGENDS",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp)

            Spacer(modifier = Modifier.height(16.dp))

            if (scores.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(Color(0xFF1C1B1F), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
                            .padding(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Grave Icon",
                            tint = Color(0xFFD0BCFF).copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "THE GRAVEYARD IS SILENT\nNo heroes have fallen yet.",
                            color = Color(0xFFE6E1E5).copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 22.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(scores.take(10)) { score ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = score.characterName.uppercase(),
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "SURVIVED: ${score.timeSurvivedSeconds / 60}m ${score.timeSurvivedSeconds % 60}s | LEVEL REACHED: ${score.levelReached}",
                                        color = Color(0xFFE6E1E5).copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }

                                Text(
                                    text = "${score.score} PTS",
                                    color = Color(0xFFD0BCFF),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 5. THE ACTIVE INGAME PLAYSCREEN
@Composable
fun PlayScreen(
    engine: GameEngine,
    onPauseToggle: () -> Unit,
    onExitGame: () -> Unit
) {
    // Collect local tick timings that recomposes PlayScreen
    var frameTick by remember { mutableStateOf(0L) }
    LaunchedEffect(key1 = engine) {
        while (true) {
            frameTick = System.currentTimeMillis()
            kotlinx.coroutines.delay(16L) // repaint at ~60fps
        }
    }

    // Touch positions track dynamics
    var joystickDown by remember { mutableStateOf(false) }
    var touchStartOffset by remember { mutableStateOf(Offset.Zero) }
    var touchCurrentOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0D13)) // Obsidian dark base
            .pointerInput(engine) {
                detectDragGestures(
                    onDragStart = { start ->
                        joystickDown = true
                        touchStartOffset = start
                        touchCurrentOffset = start
                    },
                    onDrag = { change, dragAmount ->
                        touchCurrentOffset += dragAmount
                        val dx = touchCurrentOffset.x - touchStartOffset.x
                        val dy = touchCurrentOffset.y - touchStartOffset.y
                        val joyLimit = 150f
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist > joyLimit) {
                            // Clamp joystick coordinates values
                            val ratio = joyLimit / dist
                            val rx = dx * ratio
                            val ry = dy * ratio
                            engine.updateStick(rx / joyLimit, ry / joyLimit)
                        } else {
                            engine.updateStick(dx / joyLimit, dy / joyLimit)
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        joystickDown = false
                        engine.updateStick(0f, 0f)
                    }
                )
            }
    ) {
        // Draw gameplay loop variables Canvas
        val viewWidthDp = LocalConfiguration.current.screenWidthDp
        val viewHeightDp = LocalConfiguration.current.screenHeightDp

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_field_canvas")
        ) {
            val scaleX = size.width / engine.viewWidth
            val scaleY = size.height / engine.viewHeight

            // Centered player camera calculations helper references
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val offsetX = centerX - engine.playerX
            val offsetY = centerY - engine.playerY

            // Shake camera translation
            val shakeIntensity = engine.screenShakeIntensity
            val finalOffsetX = if (shakeIntensity > 1f) {
                offsetX + (Math.random() * shakeIntensity - shakeIntensity / 2f).toFloat()
            } else offsetX
            val finalOffsetY = if (shakeIntensity > 1f) {
                offsetY + (Math.random() * shakeIntensity - shakeIntensity / 2f).toFloat()
            } else offsetY

            translate(left = finalOffsetX, top = finalOffsetY) {
                // A. Draw Scrolling endless map tiles references lines
                val tileSize = 160f
                val playerMapCol = (engine.playerX / tileSize).toInt()
                val playerMapRow = (engine.playerY / tileSize).toInt()

                val startCol = playerMapCol - 8
                val endCol = playerMapCol + 8
                val startRow = playerMapRow - 12
                val endRow = playerMapRow + 12

                for (c in startCol..endCol) {
                    for (r in startRow..endRow) {
                        val gx = c * tileSize
                        val gy = r * tileSize

                        val isChecker = (c + r) % 2 == 0
                        val colHex = if (isChecker) Color(0xFF16131C) else Color(0xFF0F0D13)

                        // Draw dark-fantasy grid tiles procedural rectangles
                        drawRect(
                            color = colHex,
                            topLeft = Offset(gx, gy),
                            size = Size(tileSize, tileSize)
                        )

                        // Add simple ground decorations markers (lilac dust specks or slate bricks)
                        val decorVal = (c * 17 + r * 23) % 40
                        if (decorVal == 5) {
                            // Subtle lilac/purple dust specks
                            drawCircle(
                                color = Color(0xFFD0BCFF).copy(alpha = 0.15f),
                                radius = 3f,
                                center = Offset(gx + tileSize / 3f, gy + tileSize / 2f)
                            )
                        } else if (decorVal == 12) {
                            // Dusty brick detail
                            drawRect(
                                color = Color(0xFF49454F).copy(alpha = 0.15f),
                                topLeft = Offset(gx + 20f, gy + 30f),
                                size = Size(20f, 6f)
                            )
                        }
                    }
                }

                // B. Draw Gems on Ground
                for (gem in engine.gems) {
                    if (gem.isGold) {
                        // Golden circle coin with cross star
                        drawCircle(color = Color(0xFFFFD54F), radius = gem.size, center = Offset(gem.x, gem.y))
                        drawCircle(color = Color(0xFFD4AF37), radius = gem.size - 2f, center = Offset(gem.x, gem.y))
                    } else {
                        // Blue diamond XP gem
                        val path = Path().apply {
                            moveTo(gem.x, gem.y - gem.size)
                            lineTo(gem.x + gem.size, gem.y)
                            lineTo(gem.x, gem.y + gem.size)
                            lineTo(gem.x - gem.size, gem.y)
                            close()
                        }
                        drawPath(path = path, color = gem.color)
                    }
                }

                // C. Draw swarming monsters
                for (enemy in engine.enemies) {
                    val frameOffset = (frameTick / 100) % 4
                    val animateY = if (enemy.enemyType == EnemyType.BAT) sin(frameOffset.toFloat()) * 4f else 0f

                    // Draw procedural pixel shapes
                    drawCircle(
                        color = enemy.color,
                        radius = enemy.size,
                        center = Offset(enemy.x, enemy.y + animateY)
                    )

                    // Add eyes dots
                    drawCircle(
                        color = Color.Red,
                        radius = 2.5f,
                        center = Offset(enemy.x - 5f, enemy.y - 2f + animateY)
                    )
                    drawCircle(
                        color = Color.Red,
                        radius = 2.5f,
                        center = Offset(enemy.x + 5f, enemy.y - 2f + animateY)
                    )

                    // Draw boss indicators
                    if (enemy.isBoss) {
                        // Gold shining frame
                        drawCircle(
                            color = Color(0xFFFFD54F),
                            radius = enemy.size + 4f,
                            center = Offset(enemy.x, enemy.y + animateY),
                            style = Stroke(width = 2.5f)
                        )

                        // Top mini boss health bar
                        val hbW = 50f
                        val hbH = 5f
                        val ratio = enemy.hp / enemy.maxHp
                        drawRect(
                            color = Color.DarkGray,
                            topLeft = Offset(enemy.x - hbW / 2, enemy.y - enemy.size - 14f),
                            size = Size(hbW, hbH)
                        )
                        drawRect(
                            color = Color.Red,
                            topLeft = Offset(enemy.x - hbW / 2, enemy.y - enemy.size - 14f),
                            size = Size(hbW * ratio, hbH)
                        )
                    }
                }

                // D. Draw Auto-attacking Neon Projectiles
                for (proj in engine.projectiles) {
                    when (proj.type) {
                        WeaponType.MAGIC_WAND, WeaponType.HOLY_SCEPTER -> {
                            // Blue neon plasma trailing bullets
                            drawCircle(color = proj.color, radius = proj.size, center = Offset(proj.x, proj.y))
                            drawCircle(color = Color.White, radius = proj.size * 0.4f, center = Offset(proj.x, proj.y))
                        }
                        WeaponType.SCYTHE -> {
                            // Pink swing sweep trails on angles
                            val swingRadius = proj.size
                            val sa = proj.angle
                            drawArc(
                                color = proj.color.copy(alpha = 0.5f),
                                startAngle = sa * 180f / PI.toFloat() - 30f,
                                sweepAngle = 75f,
                                useCenter = false,
                                topLeft = Offset(engine.playerX - swingRadius, engine.playerY - swingRadius),
                                size = Size(swingRadius * 2, swingRadius * 2),
                                style = Stroke(width = 12f)
                            )
                        }
                        WeaponType.DEATH_SCYTHE -> {
                            // Giant crimson circles of shockwaves
                            drawCircle(
                                color = proj.color.copy(alpha = 0.3f),
                                radius = proj.size,
                                center = Offset(proj.x, proj.y),
                                style = Stroke(width = 8f)
                            )
                        }
                        WeaponType.GARLIC, WeaponType.SOUL_EATER -> {
                            // Concentric pulsing aura
                            drawCircle(
                                color = proj.color.copy(alpha = 0.12f),
                                radius = proj.size,
                                center = Offset(proj.x, proj.y)
                            )
                            drawCircle(
                                color = proj.color.copy(alpha = 0.45f),
                                radius = proj.size,
                                center = Offset(proj.x, proj.y),
                                style = Stroke(width = 2.5f)
                            )
                        }
                        WeaponType.AXE, WeaponType.BLOOD_AXE -> {
                            // Orange rotating projectiles
                            val s = proj.size
                            val spinAngle = proj.angle
                            val cos = cos(spinAngle) * s
                            val sin = sin(spinAngle) * s

                            // Procedural triangle cross axe blade
                            val path = Path().apply {
                                moveTo(proj.x - cos, proj.y - sin)
                                lineTo(proj.x + cos, proj.y + sin)
                                lineTo(proj.x - sin, proj.y + cos)
                                close()
                            }
                            drawPath(path = path, color = proj.color)
                        }
                    }
                }

                // E. Draw Particles Systems (Neon Explosions!)
                for (p in engine.particles) {
                    val fadeVal = p.remainingLifespan / p.maxLifespan
                    drawCircle(
                        color = p.color.copy(alpha = fadeVal),
                        radius = p.size * fadeVal,
                        center = Offset(p.x, p.y)
                    )
                }

                // F. Draw Player Character silhouetted center
                val frameFloat = (frameTick / 140) % 4
                val playerPulseRadius = 24f + sin(frameFloat.toFloat()) * 1.5f

                // Shield outer rim glow
                drawCircle(
                    color = engine.selectedCharacter.neonColor.copy(alpha = 0.45f),
                    radius = playerPulseRadius + 12f,
                    center = Offset(engine.playerX, engine.playerY),
                    style = Stroke(width = 1.5f)
                )

                // Body Shield
                drawCircle(
                    color = engine.selectedCharacter.normalColor,
                    radius = playerPulseRadius,
                    center = Offset(engine.playerX, engine.playerY)
                )

                // Face visor dark band slot
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(engine.playerX - 16f, engine.playerY - 6f),
                    size = Size(32f, 6f)
                )

                // Dynamic mini player green lifebar hanging directly above player's head
                val plH = 4f
                val plW = 48f
                val lifeRatio = engine.playerHp / engine.playerMaxHp
                drawRect(
                    color = Color.DarkGray,
                    topLeft = Offset(engine.playerX - plW / 2, engine.playerY - 42f),
                    size = Size(plW, plH)
                )
                drawRect(
                    color = if (lifeRatio > 0.4f) Color.Green else Color.Red,
                    topLeft = Offset(engine.playerX - plW / 2, engine.playerY - 42f),
                    size = Size(plW * lifeRatio, plH)
                )

                // G. Draw Floating text damages
                for (t in engine.floatingTexts) {
                    val alpha = t.remainingLifespan / t.maxLifespan
                    // Note: Draw floating indicators procedurally inside Canvas is lighter on CPU than Compose Box tree overlays!
                    // Simple text render via draw text, but since raw compose native fonts require Paint, we can represent float text in standard draw scopes! Well, actually drawing them as Custom UI texts is simpler and looks better. Let's do that cleanly.
                }
            }
        }

        // FLOATING DAMAGING CANVAS TEXTS (rendered over in simple lightweight box elements)
        for (t in engine.floatingTexts) {
            val progress = t.remainingLifespan / t.maxLifespan
            Box(
                modifier = Modifier
                    .offset(
                        x = (t.x + touchStartOffset.x - engine.playerX - 30f).dp,
                        y = (t.y + touchStartOffset.y - engine.playerY - 30f).dp
                    )
            ) {
                Text(
                    text = t.text,
                    color = t.color.copy(alpha = progress),
                    fontSize = (t.size * 0.45f).sp, // convert canvas scale
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // LEVEL & PROGRESS TOP STATUS CHASSIS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F0D13), Color.Transparent),
                        endY = 400f
                    )
                )
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // Purple thin progress XP bar spanning edge to edge
            val xpRatio = if (engine.xpNeeded > 0) (engine.xp / engine.xpNeeded).coerceIn(0f, 1f) else 0f
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LVL ${engine.level}",
                    color = Color(0xFFD0BCFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF49454F))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(xpRatio)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFD0BCFF), Color(0xFF9070FF))
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${engine.xp.toInt()}/${engine.xpNeeded.toInt()} XP",
                    color = Color(0xFFE6E1E5).copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Game run stats: Playtime, Monsters killed, Gold coins collected
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clock Timer
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1C1B1F), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Clock",
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format("%02d:%02d", engine.timeSeconds / 60, engine.timeSeconds % 60),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Monster count
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1C1B1F), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Kills",
                        tint = Color(0xFFFFB4AB),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${engine.enemiesKilled}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Cash
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1C1B1F), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Gold Collected",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${engine.goldCollected}",
                        color = Color(0xFFFFD54F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Pause button
                IconButton(
                    onClick = onPauseToggle,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF49454F), CircleShape)
                ) {
                    Icon(
                        imageVector = if (engine.isPaused) Icons.Default.PlayArrow else Icons.Default.Close,
                        contentDescription = "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ACTIVE VIRTUAL CONTROLLER JOYSTICK WHEEL OVERLAY
        if (joystickDown) {
            val jSize = 100.dp
            val innerSize = 44.dp
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (touchStartOffset.x).toDp() - 50.dp },
                        y = with(density) { (touchStartOffset.y).toDp() - 50.dp }
                    )
                    .size(jSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Moving thumb stick position indicator
                val tX = touchCurrentOffset.x - touchStartOffset.x
                val tY = touchCurrentOffset.y - touchStartOffset.y
                val joyLimit = 150f
                val dist = sqrt(tX * tX + tY * tY)
                val cx: Float
                val cy: Float
                if (dist > joyLimit) {
                    cx = (tX / dist) * 45f
                    cy = (tY / dist) * 45f
                } else {
                    cx = (tX / joyLimit) * 45f
                    cy = (tY / joyLimit) * 45f
                }

                Box(
                    modifier = Modifier
                        .offset(x = cx.dp, y = cy.dp)
                        .size(innerSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.65f))
                        .border(1.5.dp, Color.Black, CircleShape)
                )
            }
        }

        // ACTIVE COGNITIVE PAUSE SCREEN OVERLAY
        if (engine.isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0D13).copy(alpha = 0.85f))
                    .pointerInput(Unit) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .border(1.5.dp, Color(0xFFD0BCFF), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "COMBAT PAUSED",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Text(
                            text = "Monsters are frozen in time. Analyze your arsenal and catch your breath.",
                            color = Color(0xFFE6E1E5).copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onPauseToggle,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD0BCFF),
                                    contentColor = Color(0xFF381E72)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Text(
                                    text = "RESUME COMBAT",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Button(
                                onClick = onExitGame,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2930)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                            ) {
                                Text(
                                    text = "ABANDON RUN",
                                    color = Color(0xFFE6E1E5),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // DOPAMINE-HEAVY LEVEL UP RANDOM CARDS SELECTION INTERFACES
        AnimatedVisibility(
            visible = engine.isLevelUpActive,
            enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
            exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .pointerInput(Unit) {}
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "LEVEL UP! (${engine.level})",
                        color = Color(0xFFFFD54F),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "CHOOSE ONE STRENGTH CARD",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )

                    // Card options
                    engine.activeUpgradeCards.forEach { card ->
                        val cardBorderColor = when (card.rarity) {
                            UpgradeRarity.LEGENDARY -> Color(0xFFFFD700) // Gold shimmer
                            UpgradeRarity.EPIC -> Color(0xFFD500F9)      // Purple neon
                            UpgradeRarity.RARE -> Color(0xFF00E5FF)      // Cyan
                            UpgradeRarity.COMMON -> Color(0xFF78909C)    // Slate
                        }

                        val cardBg = when (card.rarity) {
                            UpgradeRarity.LEGENDARY -> Color(0xFF292211)
                            UpgradeRarity.EPIC -> Color(0xFF24162B)
                            UpgradeRarity.RARE -> Color(0xFF14242B)
                            UpgradeRarity.COMMON -> Color(0xFF181B1C)
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { engine.applyUpgrade(card) }
                                .border(2.dp, cardBorderColor, RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = card.title,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    Box(
                                        modifier = Modifier
                                            .background(cardBorderColor, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = card.rypeLabel,
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = card.description,
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 6. VICTORY / DEFEAT GAME SUMMARY OVERLAY SCREEN
@Composable
fun GameOverScreen(
    score: Int,
    level: Int,
    timeSeconds: Int,
    goldCollected: Int,
    onReturnClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0D13)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
                .border(1.5.dp, Color(0xFF49454F), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "THE REAPER OVERWHELMED",
                    color = Color(0xFFFFB4AB),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Your ashes are scattered in the arena.\nEarn gold to permanently strengthen yourself.",
                    color = Color(0xFFE6E1E5).copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Stats Dashboard
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0D13), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "FINAL SCORE", color = Color(0xFFE6E1E5).copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(text = "$score PTS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "HIGHEST LEVEL", color = Color(0xFFE6E1E5).copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(text = "LVL $level", color = Color(0xFFD0BCFF), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "TIME SURVIVED", color = Color(0xFFE6E1E5).copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(
                            text = String.format("%02d:%02d", timeSeconds / 60, timeSeconds % 60),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "GOLD COINS", color = Color(0xFFE6E1E5).copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(text = "+$goldCollected GP 🪙", color = Color(0xFFFFD54F), fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onReturnClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD0BCFF),
                        contentColor = Color(0xFF381E72)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "RETURN TO TAVERN",
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
