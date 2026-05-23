package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.audio.GameAudioPlayer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

class GameEngine(
    val selectedCharacter: CharacterConfig,
    val permaPowerUps: Map<String, Int>,
    val soundPlayer: GameAudioPlayer,
    val onGameOver: (score: Int, levelReached: Int, timeSurvived: Int, goldEarned: Int) -> Unit
) {
    // Spatial dimensions
    val viewWidth = 1080f
    val viewHeight = 1920f

    // Player position
    var playerX = 0f
    var playerY = 0f
    var playerHp = selectedCharacter.baseMaxHp
    var playerMaxHp = selectedCharacter.baseMaxHp

    // Stats
    var score = 0
    var enemiesKilled = 0
    var level = 1
    var xp = 0f
    var xpNeeded = 15f
    var goldCollected = 0
    var timeSeconds = 0
    var isGameOver = false
    var isPaused = false

    // Upgrade Selection State
    var isLevelUpActive = false
    var activeUpgradeCards = listOf<UpgradeCard>()

    // Inputs
    var stickX = 0f
    var stickY = 0f

    // Entity lists
    val enemies = mutableListOf<Enemy>()
    val projectiles = mutableListOf<Projectile>()
    val gems = mutableListOf<XPGem>()
    val particles = mutableListOf<Particle>()
    val floatingTexts = mutableListOf<FloatingText>()

    // Selected items maps (Type -> Level)
    val weaponLevels = mutableMapOf<WeaponType, Int>()
    val passiveLevels = mutableMapOf<PassiveType, Int>()

    // Dynamic weapon cooldown timers (ms)
    private val weaponCooldownTimers = mutableMapOf<WeaponType, Long>()

    // General timing variables
    private var lastUpdateMs = System.currentTimeMillis()
    private var lastWaveSpawnMs = 0L
    private var lastWeaponFireMs = 0L
    private var secondCounterMs = 0L
    private var lastBossSpawnCheck = 0

    // Invincibility frame timer for player
    private var playerInvincibilityUntil = 0L

    // Screen shake multiplier
    var screenShakeIntensity = 0f

    private val idGenerator = AtomicInteger(1)

    init {
        // Apply permanent HP stat bonus
        val permaHpLvl = permaPowerUps["MAX_HP"] ?: 0
        playerMaxHp += permaHpLvl * 15f
        playerHp = playerMaxHp

        // Slot starting weapon
        weaponLevels[selectedCharacter.startingWeapon] = 1
        weaponCooldownTimers[selectedCharacter.startingWeapon] = 0L
    }

    fun updateStick(dx: Float, dy: Float) {
        stickX = dx
        stickY = dy
    }

    // High performance gameloop single step
    fun step(currentMs: Long) {
        if (isGameOver || isPaused || isLevelUpActive) {
            lastUpdateMs = currentMs
            return
        }

        val deltaMs = (currentMs - lastUpdateMs).coerceAtMost(50) // clamp delta to avoid extreme lag jumps
        lastUpdateMs = currentMs

        secondCounterMs += deltaMs
        if (secondCounterMs >= 1000) {
            timeSeconds++
            secondCounterMs -= 1000
            // Every 5 seconds tick score slightly for surviving
            score += 2
        }

        // 1. Process player movement
        updatePlayerMovement(deltaMs)

        // 2. Continuous weapon autotargeting & projectile fire
        fireWeapons(currentMs)

        // 3. Move projectiles
        updateProjectiles(deltaMs)

        // 4. Update and swarming logic of enemies
        updateEnemies(currentMs, deltaMs)

        // 5. Update Gems & Magnet attraction mechanics
        updateGems(deltaMs)

        // 6. Update cosmetic particle system & float text
        updateParticles(deltaMs)
        updateFloatingTexts(deltaMs)

        // 7. Limit Entity spawn counts smoothly (wave difficulty checks)
        proceduralWaveSpawning(currentMs)

        // 8. Screen shake decay
        if (screenShakeIntensity > 0f) {
            screenShakeIntensity = (screenShakeIntensity - deltaMs * 0.05f).coerceAtLeast(0f)
        }
    }

    private fun updatePlayerMovement(deltaMs: Long) {
        val speedLvl = permaPowerUps["SPEED"] ?: 0
        val passiveSpeedLvl = passiveLevels[PassiveType.WINGS] ?: 0
        val speedMult = 1f + (speedLvl * 0.08f) + (passiveSpeedLvl * 0.15f)

        val moveSpeed = selectedCharacter.baseSpeed * speedMult * 0.15f * deltaMs

        if (stickX != 0f || stickY != 0f) {
            val length = sqrt(stickX * stickX + stickY * stickY)
            val nx = stickX / length
            val ny = stickY / length

            playerX += nx * moveSpeed
            playerY += ny * moveSpeed
        }
    }

    private fun fireWeapons(currentMs: Long) {
        val mightLvl = permaPowerUps["MIGHT"] ?: 0
        val passiveMightLvl = passiveLevels[PassiveType.SPINACH] ?: 0
        val damageMult = selectedCharacter.baseDamageMultiplier * (1f + (mightLvl * 0.12f) + (passiveMightLvl * 0.15f))

        val projCountBonus = passiveLevels[PassiveType.DUPLICATOR] ?: 0

        for ((weapon, level) in weaponLevels) {
            val baseCd = getWeaponBaseCooldown(weapon)
            // Mage gets 15% CDR base, passives could add more
            val cdrMult = if (selectedCharacter.id == "imelda") 0.82f else 1.0f
            val weaponCd = (baseCd * cdrMult).toLong()

            val lastFire = weaponCooldownTimers[weapon] ?: 0L
            if (currentMs - lastFire >= weaponCd) {
                weaponCooldownTimers[weapon] = currentMs
                triggerWeaponAttack(weapon, level, damageMult, projCountBonus, currentMs)
            }
        }
    }

    private fun triggerWeaponAttack(weapon: WeaponType, lvl: Int, dmgMult: Float, projsBonus: Int, currentMs: Long) {
        when (weapon) {
            WeaponType.MAGIC_WAND -> {
                // Shoot ice fireballs towards closest enemy
                soundPlayer.playSlash()
                val target = findClosestEnemy()
                val baseDamage = 15f + lvl * 5f
                val damage = baseDamage * dmgMult
                val quantity = 1 + projsBonus + (lvl / 2) // extra proj at lvl 3 and 5

                var angle = if (target != null) atan2(target.y - playerY, target.x - playerX) else 0f
                val spread = 0.25f // radians spread

                for (i in 0 until quantity) {
                    val actualAngle = angle + (i - (quantity - 1) / 2f) * spread
                    val vx = cos(actualAngle) * 12f
                    val vy = sin(actualAngle) * 12f
                    projectiles.add(
                        Projectile(
                            x = playerX,
                            y = playerY,
                            vx = vx,
                            vy = vy,
                            damage = damage,
                            size = 18f + lvl * 1.5f,
                            color = Color(0xFF00E5FF), // cyan neon
                            type = WeaponType.MAGIC_WAND,
                            pierceLeft = 1 + lvl / 2
                        )
                    )
                }
            }
            WeaponType.HOLY_SCEPTER -> {
                // Evolved wand! Fires infinite pierce tracking plasma balls super fast
                soundPlayer.playSlash()
                val target = findClosestEnemy()
                val damage = 42f * dmgMult
                val quantity = 3 + projsBonus
                var angle = if (target != null) atan2(target.y - playerY, target.x - playerX) else 0f
                val spread = 0.20f

                for (i in 0 until quantity) {
                    val actualAngle = angle + (i - (quantity - 1) / 2f) * spread
                    val vx = cos(actualAngle) * 16f
                    val vy = sin(actualAngle) * 16f
                    projectiles.add(
                        Projectile(
                            x = playerX,
                            y = playerY,
                            vx = vx,
                            vy = vy,
                            damage = damage,
                            size = 28f,
                            color = Color(0xFFFF00E5), // neon pink tracking
                            type = WeaponType.HOLY_SCEPTER,
                            pierceLeft = 100 // essentially infinite
                        )
                    )
                }
            }
            WeaponType.SCYTHE -> {
                // Wide circular slashes around the player
                soundPlayer.playSlash()
                val damage = (20f + lvl * 8f) * dmgMult
                val size = 90f + lvl * 15f
                val count = 1 + projsBonus

                for (c in 0 until count) {
                    // Generate rotation projectile with short lifetime
                    val spawnAngle = (c * (2 * PI / count)).toFloat()
                    projectiles.add(
                        Projectile(
                            x = playerX,
                            y = playerY,
                            vx = 0f,
                            vy = 0f,
                            damage = damage,
                            size = size,
                            color = Color(0xFFE91E63), // hot pink slash arc
                            type = WeaponType.SCYTHE,
                            pierceLeft = 12, // pierces many enemies
                            angle = spawnAngle,
                            angularSpeed = 0.12f,
                            lifespan = 450L
                        )
                    )
                }
            }
            WeaponType.DEATH_SCYTHE -> {
                // Giant radiating scarlet waves of doom
                soundPlayer.playSlash()
                val damage = 75f * dmgMult
                val size = 200f
                val count = 2 + projsBonus

                for (c in 0 until count) {
                    val waveAngle = (c * (2 * PI / count) + (currentMs % 3600) / 1000f).toFloat()
                    // Radiates outwards
                    val vx = cos(waveAngle) * 6f
                    val vy = sin(waveAngle) * 6f
                    projectiles.add(
                        Projectile(
                            x = playerX,
                            y = playerY,
                            vx = vx,
                            vy = vy,
                            damage = damage,
                            size = size,
                            color = Color(0xFFFF1744), // giant neon blood red scythe wave
                            type = WeaponType.DEATH_SCYTHE,
                            pierceLeft = 99,
                            angle = waveAngle,
                            lifespan = 1200L
                        )
                    )
                }
            }
            WeaponType.GARLIC -> {
                // Continuous local damage aura.
                // It stays centered on player and damages enemies checking overlapping ticks.
                // Just respawn the garlic dome. It ticks down immediately.
                val damage = (4f + lvl * 2f) * dmgMult
                val size = 110f + lvl * 18f
                projectiles.add(
                    Projectile(
                        x = playerX,
                        y = playerY,
                        vx = 0f,
                        vy = 0f,
                        damage = damage,
                        size = size,
                        color = Color(0xFFC0CA33), // slimy neon lime-yellow garlic field
                        type = WeaponType.GARLIC,
                        pierceLeft = 999, // infinite
                        lifespan = 300L
                    )
                )
            }
            WeaponType.SOUL_EATER -> {
                // Giant black hole matrix that recovers HP upon combat tick damage
                val damage = 14f * dmgMult
                val size = 220f
                projectiles.add(
                    Projectile(
                        x = playerX,
                        y = playerY,
                        vx = 0f,
                        vy = 0f,
                        damage = damage,
                        size = size,
                        color = Color(0xFF7E57C2), // glowing purple soul collector field
                        type = WeaponType.SOUL_EATER,
                        pierceLeft = 999,
                        lifespan = 350L
                    )
                )
            }
            WeaponType.AXE -> {
                // Fire upward axes dropping downwards
                soundPlayer.playSlash()
                val damage = (25f + lvl * 9f) * dmgMult
                val size = 45f + lvl * 6f
                val count = 1 + projsBonus + (lvl / 3)

                for (i in 0 until count) {
                    val angleOffset = (i * 0.3f) - (count - 1) * 0.15f
                    val vx = angleOffset * 4f
                    val vy = -18f - (Math.random() * 4f).toFloat() // strong upward force
                    projectiles.add(
                        Projectile(
                            x = playerX,
                            y = playerY,
                            vx = vx,
                            vy = vy,
                            damage = damage,
                            size = size,
                            color = Color(0xFFFF9100), // neon orange spinning outline
                            type = WeaponType.AXE,
                            pierceLeft = 3 + lvl,
                            lifespan = 2500L,
                            angularSpeed = 0.25f
                        )
                    )
                }
            }
            WeaponType.BLOOD_AXE -> {
                // Massive blood meteors falling down directly on grouped positions
                soundPlayer.playSlash()
                val damage = 90f * dmgMult
                val size = 110f
                val count = 4 + projsBonus * 2

                for (i in 0 until count) {
                    // Falls near player
                    val tx = playerX + (Math.random() * 800 - 400).toFloat()
                    val ty = playerY - 1000f - (Math.random() * 300).toFloat()
                    val vx = (Math.random() * 4f - 2f).toFloat()
                    val vy = 15f + (Math.random() * 8f).toFloat() // meteor cascading downwards

                    projectiles.add(
                        Projectile(
                            x = tx,
                            y = ty,
                            vx = vx,
                            vy = vy,
                            damage = damage,
                            size = size,
                            color = Color(0xFFFF3D00), // explosive blood-orange meteor
                            type = WeaponType.BLOOD_AXE,
                            pierceLeft = 99,
                            lifespan = 1800L,
                            angularSpeed = 0.15f
                        )
                    )
                }
            }
        }
    }

    private fun getWeaponBaseCooldown(weapon: WeaponType): Long {
        return when (weapon) {
            WeaponType.MAGIC_WAND -> 1100L
            WeaponType.HOLY_SCEPTER -> 300L // rapid automatic barrage!
            WeaponType.SCYTHE -> 1600L
            WeaponType.DEATH_SCYTHE -> 1200L
            WeaponType.GARLIC -> 450L
            WeaponType.SOUL_EATER -> 350L
            WeaponType.AXE -> 2000L
            WeaponType.BLOOD_AXE -> 1500L
        }
    }

    private fun updateProjectiles(deltaMs: Long) {
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val proj = iterator.next()
            val age = System.currentTimeMillis() - proj.spawnTime

            if (age >= proj.lifespan || proj.pierceLeft <= 0) {
                iterator.remove()
                continue
            }

            if (proj.type == WeaponType.MAGIC_WAND || proj.type == WeaponType.HOLY_SCEPTER) {
                // Simple auto homing if nearest enemy exists
                val enemy = findClosestEnemy()
                if (enemy != null) {
                    val angleTo = atan2(enemy.y - proj.y, enemy.x - proj.x)
                    val speed = sqrt(proj.vx * proj.vx + proj.vy * proj.vy)
                    proj.vx = cos(angleTo) * speed
                    proj.vy = sin(angleTo) * speed
                }
                proj.x += proj.vx * (deltaMs / 16.6f)
                proj.y += proj.vy * (deltaMs / 16.6f)
            } else if (proj.type == WeaponType.GARLIC || proj.type == WeaponType.SOUL_EATER) {
                // Stays fixed precisely on player
                proj.x = playerX
                proj.y = playerY
            } else if (proj.type == WeaponType.SCYTHE) {
                // Orbits the player closely
                proj.angle += proj.angularSpeed
                val radius = 110f
                proj.x = playerX + cos(proj.angle) * radius
                proj.y = playerY + sin(proj.angle) * radius
            } else if (proj.type == WeaponType.AXE || proj.type == WeaponType.BLOOD_AXE) {
                // Gravity downward falls
                proj.vy += 0.8f * (deltaMs / 16.6f) // gravity pull
                proj.x += proj.vx * (deltaMs / 16.6f)
                proj.y += proj.vy * (deltaMs / 16.6f)
                proj.angle += proj.angularSpeed
            } else {
                proj.x += proj.vx * (deltaMs / 16.6f)
                proj.y += proj.vy * (deltaMs / 16.6f)
                proj.angle += proj.angularSpeed
            }
        }
    }

    private fun updateEnemies(currentMs: Long, deltaMs: Long) {
        val iterator = enemies.iterator()
        val tickRateModifier = deltaMs / 16.6f

        while (iterator.hasNext()) {
            val enemy = iterator.next()

            // Handle dead enemy
            if (enemy.hp <= 0f) {
                iterator.remove()
                score += enemy.xpValue * 10
                enemiesKilled++

                // Roll for spawn reward drops (Gold or XP gem)
                val greedLvl = permaPowerUps["GREED"] ?: 0
                val passiveCrownLvl = passiveLevels[PassiveType.CROWN] ?: 0
                val goldMultiplier = 1f + (greedLvl * 0.15f)

                val roll = Math.random()
                val isGold = roll < 0.18 + (greedLvl * 0.05) // higher loot chance
                val amount = if (isGold) {
                    val maxGold = if (enemy.isBoss) 60 else 10
                    (Math.random() * maxGold * goldMultiplier).toInt().coerceAtLeast(1)
                } else {
                    val xpGainMult = 1f + (passiveCrownLvl * 0.15f)
                    (enemy.xpValue * xpGainMult).toInt()
                }

                spawnGem(enemy.x, enemy.y, amount = amount, isGold = isGold)

                // Exploding massive particle stars! Double volume on Bosses
                val partCount = if (enemy.isBoss) 24 else 8
                spawnExplosion(enemy.x, enemy.y, enemy.color, partCount)

                continue
            }

            // Simple steering towards the player
            val dx = playerX - enemy.x
            val dy = playerY - enemy.y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist > 2500f) {
                // despawn enemies that wander way too far out to conserve computation
                iterator.remove()
                continue
            }

            if (dist > 5f) {
                // Move towards player
                enemy.x += (dx / dist) * enemy.speed * tickRateModifier
                enemy.y += (dy / dist) * enemy.speed * tickRateModifier
            }

            // Player collision damage deal check
            if (dist < (enemy.size + 24f) && currentMs >= playerInvincibilityUntil) {
                dealDamageToPlayer(enemy.damage, currentMs)
            }

            // Check collision with ALL active projectiles (Circle intersection)
            for (proj in projectiles) {
                val pdx = proj.x - enemy.x
                val pdy = proj.y - enemy.y
                val pdist = sqrt(pdx * pdx + pdy * pdy)
                val hitDistance = proj.size + enemy.size

                if (pdist < hitDistance) {
                    // Guard Garlic multi-ticks: garlic damage can only tick once per entity per cast lifespan
                    val garlicDamageMapKey = "garlic_hit_${proj.type}_${proj.spawnTime}_${enemy.id}"
                    // Simple damage registers which drops pierce counts
                    if (proj.type == WeaponType.GARLIC || proj.type == WeaponType.SOUL_EATER) {
                        // Quick tick-rate filter so garlic doesn't instant kill
                        val timeSinceLastGarlicDmg = currentMs % 350
                        if (timeSinceLastGarlicDmg < 40) {
                            dealDamageToEnemy(enemy, proj.damage, currentMs)
                            if (proj.type == WeaponType.SOUL_EATER) {
                                // Lifesteal logic
                                val recoverChance = 0.15
                                if (Math.random() < recoverChance && playerHp < playerMaxHp) {
                                    playerHp = (playerHp + 1f).coerceAtMost(playerMaxHp)
                                    spawnFloatingText("+1 HP", playerX, playerY - 40f, Color(0xFF81C784), 30f)
                                }
                            }
                        }
                    } else {
                        // Regular bullet: pierce count decreases
                        proj.pierceLeft--
                        dealDamageToEnemy(enemy, proj.damage, currentMs)

                        // Visual knockback force
                        val kX = (enemy.x - playerX)
                        val kY = (enemy.y - playerY)
                        val kLength = sqrt(kX * kX + kY * kY)
                        if (kLength > 0.1f) {
                            enemy.x += (kX / kLength) * 8f
                            enemy.y += (kY / kLength) * 8f
                        }
                    }
                }
            }
        }
    }

    private fun dealDamageToPlayer(damageAmt: Float, currentMs: Long) {
        // Simple damage mitigation (unlocked stat values)
        val loss = damageAmt.coerceAtLeast(1f)
        playerHp = (playerHp - loss).coerceAtLeast(0f)

        soundPlayer.playHurt()
        playerInvincibilityUntil = currentMs + 500 // 500ms invincibility frames
        screenShakeIntensity = 18f

        // Float red warning above player
        spawnFloatingText("-${loss.toInt()}", playerX, playerY - 50f, Color(0xFFFF5252), 34f)

        if (playerHp <= 0f) {
            triggerGameOver()
        }
    }

    private fun dealDamageToEnemy(enemy: Enemy, rawDamage: Float, currentMs: Long) {
        val isCrit = Math.random() < 0.12
        val damage = if (isCrit) rawDamage * 2.2f else rawDamage
        enemy.hp -= damage

        // Flash text
        val textCol = if (isCrit) Color(0xFFFFEB3B) else Color.White // Yellow for crit, white normal
        val textScale = if (isCrit) 34f else 24f
        val textLabel = if (isCrit) "${damage.toInt()}🔥" else "${damage.toInt()}"

        spawnFloatingText(textLabel, enemy.x, enemy.y - 15f * Math.random().toFloat(), textCol, textScale)

        // Spawn bright micro slice particles
        spawnExplosion(enemy.x, enemy.y, textCol, 3)

        // Shake viewport on giant damage
        if (isCrit) screenShakeIntensity = 4.5f
    }

    private fun updateGems(deltaMs: Long) {
        val magnetLvl = permaPowerUps["MAGNET"] ?: 0
        val passiveMagnetLvl = passiveLevels[PassiveType.ATTRACTOR] ?: 0
        val baseMagnetRadius = 80f
        val attractionRadius = baseMagnetRadius * (1f + magnetLvl * 0.25f + passiveMagnetLvl * 0.40f)

        val iterator = gems.iterator()
        while (iterator.hasNext()) {
            val gem = iterator.next()

            val dx = playerX - gem.x
            val dy = playerY - gem.y
            val dist = sqrt(dx * dx + dy * dy)

            // Within collect hitbox?
            if (dist < 32f) {
                // Collect gem!
                iterator.remove()
                soundPlayer.playCoin()

                if (gem.isGold) {
                    goldCollected += gem.amount
                    score += gem.amount * 5
                    spawnFloatingText("+${gem.amount} Gold🪙", gem.x, gem.y - 35f, Color(0xFFFFD700), 28f)
                    spawnExplosion(gem.x, gem.y, Color(0xFFFFD54F), 6)
                } else {
                    addXp(gem.xpValue.toFloat())
                    spawnExplosion(gem.x, gem.y, Color(0xFF00E5FF), 4)
                }
                continue
            }

            // Attracted?
            if (gem.isAttracted || dist < attractionRadius) {
                gem.isAttracted = true
                val pullSpeed = 12f + (attractionRadius / dist) * 2f
                gem.x += (dx / dist) * pullSpeed * (deltaMs / 16.6f)
                gem.y += (dy / dist) * pullSpeed * (deltaMs / 16.6f)
            }
        }
    }

    private fun addXp(amount: Float) {
        xp += amount
        if (xp >= xpNeeded) {
            xp -= xpNeeded
            triggerLevelUp()
        }
    }

    private fun triggerLevelUp() {
        level++
        xpNeeded = level * 10f + 15f
        soundPlayer.playLevelUp()
        screenShakeIntensity = 12f
        isLevelUpActive = true
        activeUpgradeCards = generateUpgradeCards()
    }

    private fun triggerGameOver() {
        isGameOver = true
        onGameOver(score, level, timeSeconds, goldCollected)
    }

    // High fidelity visual particle systems
    private fun spawnExplosion(x: Float, y: Float, color: Color, count: Int) {
        for (i in 0 until count) {
            val angle = (Math.random() * 2 * PI).toFloat()
            val speed = (2f + Math.random() * 8f).toFloat()
            val vx = cos(angle) * speed
            val vy = sin(angle) * speed
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy,
                    color = color,
                    size = (4f + Math.random() * 6f).toFloat(),
                    maxLifespan = 350f,
                    remainingLifespan = 350f,
                    isNeon = true
                )
            )
        }
    }

    private fun spawnGem(x: Float, y: Float, amount: Int, isGold: Boolean) {
        val color = if (isGold) Color(0xFFFFD54F) else when {
            amount >= 15 -> Color(0xFFFF1744) // mega ruby gem
            amount >= 5 -> Color(0xFF00E676)  // emerald gem
            else -> Color(0xFF29B6F6)         // regular sapphire sapphire gem
        }

        gems.add(
            XPGem(
                id = idGenerator.incrementAndGet(),
                x = x,
                y = y,
                xpValue = amount,
                isGold = isGold,
                amount = amount,
                color = color,
                size = if (amount >= 15) 12f else if (amount >= 5) 9f else 7f
            )
        )
    }

    private fun spawnFloatingText(text: String, x: Float, y: Float, color: Color, size: Float) {
        floatingTexts.add(
            FloatingText(
                text = text,
                x = x,
                y = y,
                color = color,
                size = size,
                vy = -3f - Math.random().toFloat() * 2f,
                maxLifespan = 600f,
                remainingLifespan = 600f
            )
        )
    }

    private fun updateParticles(deltaMs: Long) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.remainingLifespan -= deltaMs
            if (p.remainingLifespan <= 0f) {
                iterator.remove()
                continue
            }
            // physics step
            p.x += p.vx * (deltaMs / 16.6f)
            p.y += p.vy * (deltaMs / 16.6f)
            // gravity / drag slide
            p.vx *= 0.95f
            p.vy *= 0.95f
        }
    }

    private fun updateFloatingTexts(deltaMs: Long) {
        val iterator = floatingTexts.iterator()
        while (iterator.hasNext()) {
            val t = iterator.next()
            t.remainingLifespan -= deltaMs
            if (t.remainingLifespan <= 0f) {
                iterator.remove()
                continue
            }
            t.y += t.vy * (deltaMs / 16.6f)
            t.vy *= 0.94f // friction drag vertical rising slow
        }
    }

    private fun proceduralWaveSpawning(currentMs: Long) {
        // Spawns waves every 30s. The difficulty scales up over survival time.
        val survivalMin = timeSeconds / 60
        val isEliteNeeded = timeSeconds % 45 == 0 && timeSeconds > 0

        // Check boss triggers
        if (timeSeconds > 0 && timeSeconds / 60 > lastBossSpawnCheck) {
            lastBossSpawnCheck = timeSeconds / 60
            spawnBossEvent(lastBossSpawnCheck)
        }

        if (currentMs - lastWaveSpawnMs < 1200L) return
        lastWaveSpawnMs = currentMs

        // Maximum onscreen entities caps to prevent lag
        if (enemies.size >= 180) return

        val spawnCount = (8 + survivalMin * 4 + level / 2).coerceAtMost(25)
        for (i in 0 until spawnCount) {
            // Spawns randomly in a large bounding shell offscreen relative to viewport
            val radius = 600f + (Math.random() * 200).toFloat()
            val angle = (Math.random() * 2 * PI).toFloat()
            val ex = playerX + cos(angle) * radius
            val ey = playerY + sin(angle) * radius

            // Determine enemy type based on survival minutes
            val roll = Math.random()
            val type: EnemyType
            val color: Color
            var maxHp = 10f + level * 3f
            var speed = 1.8f
            var dmg = 3f + survivalMin * 1.5f
            var xp = 1

            when {
                survivalMin >= 4 -> {
                    // Skeletons and ghosts
                    if (roll < 0.4) {
                        type = EnemyType.SKELETON
                        color = Color(0xFFEEEEEE)
                        maxHp = 45f
                        speed = 1.9f
                        dmg = 8f
                        xp = 3
                    } else if (roll < 0.8) {
                        type = EnemyType.GHOST
                        color = Color(0x99B2DFDB) // transparent teal
                        maxHp = 25f
                        speed = 1.4f
                        dmg = 5f
                        xp = 2
                    } else {
                        type = EnemyType.WEREWOLF
                        color = Color(0xFF8D6E63)
                        maxHp = 80f
                        speed = 2.4f
                        dmg = 12f
                        xp = 5
                    }
                }
                survivalMin >= 2 -> {
                    // Werewolves and ghosts
                    if (roll < 0.5) {
                        type = EnemyType.GHOST
                        color = Color(0x9980DEEA)
                        maxHp = 20f
                        speed = 1.3f
                        dmg = 4f
                        xp = 2
                    } else {
                        type = EnemyType.WEREWOLF
                        color = Color(0xFF795548)
                        maxHp = 50f
                        speed = 2.0f
                        dmg = 9f
                        xp = 4
                    }
                }
                else -> {
                    // bats and zombies
                    if (roll < 0.6) {
                        type = EnemyType.BAT
                        color = Color(0xFFAB47BC) // purple bat
                        maxHp = 10f + level * 2f
                        speed = 2.6f
                        dmg = 3f
                        xp = 1
                    } else {
                        type = EnemyType.ZOMBIE
                        color = Color(0xFF43A047) // green zombie
                        maxHp = 25f + level * 4f
                        speed = 1.2f
                        dmg = 6f
                        xp = 2
                    }
                }
            }

            enemies.add(
                Enemy(
                    id = idGenerator.incrementAndGet(),
                    x = ex,
                    y = ey,
                    hp = maxHp,
                    maxHp = maxHp,
                    speed = speed,
                    damage = dmg,
                    size = 18f,
                    enemyType = type,
                    color = color,
                    xpValue = xp
                )
            )
        }
    }

    private fun spawnBossEvent(minute: Int) {
        soundPlayer.playBossSpawn()
        screenShakeIntensity = 30f

        val radius = 550f
        val angle = (Math.random() * 2 * PI).toFloat()
        val bx = playerX + cos(angle) * radius
        val by = playerY + sin(angle) * radius

        val type: EnemyType
        val label: String
        val hp: Float
        val col: Color
        val goldValue = 25 * minute

        if (minute >= 3) {
            type = EnemyType.REAPER_BOSS
            label = "NEON RED GRIM REAPER BOSS"
            hp = 850f + level * 100f
            col = Color(0xFFD50000) // Glowing Crimson red
        } else if (minute == 2) {
            type = EnemyType.ELITE_BOSS
            label = "WEREWOLF OVERLORD BOSS"
            hp = 450f
            col = Color(0xFFFF6D00) // Deep fiery amber
        } else {
            type = EnemyType.MINI_BOSS
            label = "EMPRESS BAT QUEEN BOSS"
            hp = 200f
            col = Color(0xFF8E24AA) // royal purple
        }

        enemies.add(
            Enemy(
                id = idGenerator.incrementAndGet(),
                x = bx,
                y = by,
                hp = hp,
                maxHp = hp,
                speed = 1.6f,
                damage = 15f + minute * 5f,
                size = 45f,
                enemyType = type,
                color = col,
                xpValue = 18 + minute * 10,
                isBoss = true
            )
        )

        // Broadcast flying warn banner
        spawnFloatingText("⚠️ WARNING: BOSS INBOUND ⚠️", playerX, playerY - 300f, Color.Red, 42f)
    }

    private fun generateUpgradeCards(): List<UpgradeCard> {
        // Check current inventories and construct choices
        val cards = mutableListOf<UpgradeCard>()

        // Helper lists
        val currentWeapons = weaponLevels.keys.toList()
        val currentPassives = passiveLevels.keys.toList()

        // 1. CHECKS MET EVOLUTIONS
        // Holy scepter (Wand Lvl 5 + Crown passive)
        if (weaponLevels[WeaponType.MAGIC_WAND] == 5 && passiveLevels.containsKey(PassiveType.CROWN) && !weaponLevels.containsKey(WeaponType.HOLY_SCEPTER)) {
            cards.add(
                UpgradeCard(
                    id = "EVOLVE_HOLY_SCEPTER",
                    title = "✨ HOLY SCEPTER ✨",
                    description = "Arcane Evolved! Shoots high-damage neon tracks at incredible rate with infinite piercing.",
                    rypeLabel = "CHRONO EVOLUTION",
                    rarity = UpgradeRarity.LEGENDARY,
                    isWeapon = true,
                    isEvolution = true,
                    weaponType = WeaponType.HOLY_SCEPTER
                )
            )
        }
        // Death Scythe (Scythe lvl 5 + Hollow Heart)
        if (weaponLevels[WeaponType.SCYTHE] == 5 && passiveLevels.containsKey(PassiveType.HOLLOW_HEART) && !weaponLevels.containsKey(WeaponType.DEATH_SCYTHE)) {
            cards.add(
                UpgradeCard(
                    id = "EVOLVE_DEATH_SCYTHE",
                    title = "💀 DEATH SCYTHE 💀",
                    description = "Sacred Evolved! Shoots enormous shockwave rings of blood red death.",
                    rypeLabel = "CHRONO EVOLUTION",
                    rarity = UpgradeRarity.LEGENDARY,
                    isWeapon = true,
                    isEvolution = true,
                    weaponType = WeaponType.DEATH_SCYTHE
                )
            )
        }
        // Soul Eater (Garlic lvl 5 + Spinach)
        if (weaponLevels[WeaponType.GARLIC] == 5 && passiveLevels.containsKey(PassiveType.SPINACH) && !weaponLevels.containsKey(WeaponType.SOUL_EATER)) {
            cards.add(
                UpgradeCard(
                    id = "EVOLVE_SOUL_EATER",
                    title = "🔮 SOUL EATER 🔮",
                    description = "Gargantuan Pulsing Aura! Ticks heavy vacuum fields and leeches enemy health to restore maximum HP.",
                    rypeLabel = "CHRONO EVOLUTION",
                    rarity = UpgradeRarity.LEGENDARY,
                    isWeapon = true,
                    isEvolution = true,
                    weaponType = WeaponType.SOUL_EATER
                )
            )
        }
        // Blood Axe (Axe Lvl 5 + Wings)
        if (weaponLevels[WeaponType.AXE] == 5 && passiveLevels.containsKey(PassiveType.WINGS) && !weaponLevels.containsKey(WeaponType.BLOOD_AXE)) {
            cards.add(
                UpgradeCard(
                    id = "EVOLVE_BLOOD_AXE",
                    title = "☄️ BLOOD AXE ☄️",
                    description = "Cataclysm Evolved! Cascades explosive fireballs and meteors straight down on enemy clusters.",
                    rypeLabel = "CHRONO EVOLUTION",
                    rarity = UpgradeRarity.LEGENDARY,
                    isWeapon = true,
                    isEvolution = true,
                    weaponType = WeaponType.BLOOD_AXE
                )
            )
        }

        // 2. Regular Item Levelups
        for ((weapon, lvl) in weaponLevels) {
            val maxLvl = 5
            if (lvl < maxLvl) {
                val nextLvl = lvl + 1
                val rState = getWeaponRarity(weapon, nextLvl)
                cards.add(
                    UpgradeCard(
                        id = "WEAPON_LVL_${weapon}_$nextLvl",
                        title = "${getCleanWeaponName(weapon)} (Lvl $nextLvl)",
                        description = getWeaponLvlDescription(weapon, nextLvl),
                        rypeLabel = "WEAPON UPGRADE",
                        rarity = rState,
                        isWeapon = true,
                        weaponType = weapon
                    )
                )
            }
        }

        for ((passive, lvl) in passiveLevels) {
            val maxLvl = 5
            if (lvl < maxLvl) {
                val nextLvl = lvl + 1
                val rState = getPassiveRarity(passive, nextLvl)
                cards.add(
                    UpgradeCard(
                        id = "PASSIVE_LVL_${passive}_$nextLvl",
                        title = "${getCleanPassiveName(passive)} (Lvl $nextLvl)",
                        description = getPassiveLvlDescription(passive, nextLvl),
                        rypeLabel = "PASSIVE ITEM UPGRADE",
                        rarity = rState,
                        isWeapon = false,
                        isPassive = true,
                        passiveType = passive
                    )
                )
            }
        }

        // 3. New Weapon Options
        val openWeaponSlots = 6 - weaponLevels.size
        if (openWeaponSlots > 0) {
            val possibleNewWeapons = WeaponType.values().filter {
                it != WeaponType.HOLY_SCEPTER && it != WeaponType.DEATH_SCYTHE &&
                it != WeaponType.SOUL_EATER && it != WeaponType.BLOOD_AXE &&
                !weaponLevels.containsKey(it)
            }
            for (newW in possibleNewWeapons) {
                cards.add(
                    UpgradeCard(
                        id = "NEW_WEAPON_$newW",
                        title = "Get ${getCleanWeaponName(newW)} (Lvl 1)",
                        description = getWeaponLvlDescription(newW, 1),
                        rypeLabel = "NEW WEAPON SLOT",
                        rarity = UpgradeRarity.COMMON,
                        isWeapon = true,
                        weaponType = newW
                    )
                )
            }
        }

        // 4. New Passive Options
        val openPassiveSlots = 6 - passiveLevels.size
        if (openPassiveSlots > 0) {
            val possibleNewPassives = PassiveType.values().filter { !passiveLevels.containsKey(it) }
            for (newP in possibleNewPassives) {
                cards.add(
                    UpgradeCard(
                        id = "NEW_PASSIVE_$newP",
                        title = "Get ${getCleanPassiveName(newP)} (Lvl 1)",
                        description = getPassiveLvlDescription(newP, 1),
                        rypeLabel = "NEW PASSIVE ACCESSORY",
                        rarity = UpgradeRarity.COMMON,
                        isWeapon = false,
                        isPassive = true,
                        passiveType = newP
                    )
                )
            }
        }

        // Shuffles choices, returns 3 or 4 cards max.
        cards.shuffle()

        // Fallbacks if no standard cards left (healing / gold card)
        if (cards.size < 3) {
            cards.add(
                UpgradeCard(
                    id = "STAT_HEAL",
                    title = "❤️ Cooked Meat Feast",
                    description = "Instantly heals +40 HP to keep you surviving.",
                    rypeLabel = "EMERGENCY SUPPLY",
                    rarity = UpgradeRarity.COMMON,
                    isWeapon = false
                )
            )
            cards.add(
                UpgradeCard(
                    id = "STAT_GOLD_BURST",
                    title = "💰 Treasure Sack",
                    description = "Instantly awards you +50 Gold coins.",
                    rypeLabel = "EMERGENCY REWARD",
                    rarity = UpgradeRarity.RARE,
                    isWeapon = false
                )
            )
        }

        return cards.take(if (level >= 10) 4 else 3)
    }

    fun applyUpgrade(card: UpgradeCard) {
        if (card.id == "STAT_HEAL") {
            playerHp = (playerHp + 40f).coerceAtMost(playerMaxHp)
            soundPlayer.playCoin()
        } else if (card.id == "STAT_GOLD_BURST") {
            goldCollected += 50
            score += 250
            soundPlayer.playCoin()
        } else if (card.isEvolution) {
            soundPlayer.playEvolve()
            val evolved = card.weaponType!!
            val parent = when (evolved) {
                WeaponType.HOLY_SCEPTER -> WeaponType.MAGIC_WAND
                WeaponType.DEATH_SCYTHE -> WeaponType.SCYTHE
                WeaponType.SOUL_EATER -> WeaponType.GARLIC
                WeaponType.BLOOD_AXE -> WeaponType.AXE
                else -> WeaponType.MAGIC_WAND
            }
            weaponLevels.remove(parent)
            weaponLevels[evolved] = 1
            weaponCooldownTimers[evolved] = 0L

            // Show flash on selection
            screenShakeIntensity = 40f
            spawnFloatingText("🌟 EVOLUTION OF POWER STATE ACHIEVED! 🌟", playerX, playerY - 120f, Color.Yellow, 40f)
        } else if (card.isWeapon) {
            val weapon = card.weaponType!!
            val currentLvl = weaponLevels[weapon] ?: 0
            weaponLevels[weapon] = currentLvl + 1
            weaponCooldownTimers[weapon] = 0L
        } else if (card.isPassive) {
            val passive = card.passiveType!!
            val currentLvl = passiveLevels[passive] ?: 0
            val nextLvl = currentLvl + 1
            passiveLevels[passive] = nextLvl

            // Re-apply max HP changes if Hollow Heart upgraded
            if (passive == PassiveType.HOLLOW_HEART) {
                val deltaHp = 20f
                playerMaxHp += deltaHp
                playerHp += deltaHp
            }
        }

        // Resume engine
        isLevelUpActive = false
    }

    private fun findClosestEnemy(): Enemy? {
        var closest: Enemy? = null
        var bestDist = Float.MAX_VALUE
        for (e in enemies) {
            val dx = e.x - playerX
            val dy = e.y - playerY
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                closest = e
            }
        }
        return closest
    }

    // Helper functions for names and content formatting
    fun getCleanWeaponName(wt: WeaponType) = when (wt) {
        WeaponType.MAGIC_WAND -> "Magic Wand"
        WeaponType.HOLY_SCEPTER -> "Holy Scepter ⚡"
        WeaponType.SCYTHE -> "Scythe"
        WeaponType.DEATH_SCYTHE -> "Shadow Death Scythe 💀"
        WeaponType.GARLIC -> "Garlic Protection"
        WeaponType.SOUL_EATER -> "Soul Eater Portal 🔮"
        WeaponType.AXE -> "Iron Battle Axe"
        WeaponType.BLOOD_AXE -> "Vampiric Blood Axe ☄️"
    }

    fun getCleanPassiveName(pt: PassiveType) = when (pt) {
        PassiveType.CROWN -> "Golden Crown"
        PassiveType.HOLLOW_HEART -> "Hollow Drake Heart"
        PassiveType.SPINACH -> "Demon Spinach"
        PassiveType.WINGS -> "Pegasus Wings"
        PassiveType.DUPLICATOR -> "Ring Duplicator"
        PassiveType.ATTRACTOR -> "Magnet Orb"
    }

    private fun getWeaponLvlDescription(wt: WeaponType, lvl: Int) = when (wt) {
        WeaponType.MAGIC_WAND -> when (lvl) {
            1 -> "Shoots rapid trackable ice bolts at the nearest foe."
            2 -> "+1 projectile. Increased projectile damage."
            3 -> "+25% size. Lowers attack cooldown."
            4 -> "+1 bullet and pierce count increments by 1."
            else -> "Max Level! Perfect tool for Holy Evolve combo."
        }
        WeaponType.HOLY_SCEPTER -> "Archemist evolved! Fires a nonstop stream of infinite pierce energy bolts."
        WeaponType.SCYTHE -> when (lvl) {
            1 -> "Slashes in a sweeping circular arc around the player."
            2 -> "+30% damage. Sweeps larger range."
            3 -> "+1 additional simultaneous slice scythe projectile."
            4 -> "+50% pierce count for dense corridors."
            else -> "Max Level! Ready to evolve into Death Scythe ultimate form."
        }
        WeaponType.DEATH_SCYTHE -> "Runic evolved! Radiates massive screenshaking circles of crimson scythe light."
        WeaponType.GARLIC -> when (lvl) {
            1 -> "Surrounds the player with a continuous light damaging aura."
            2 -> "+15% size. Increases ticking damage rate."
            3 -> "Slight knockback multiplier. Garlic fields widen."
            4 -> "+20% Garlic damage and reduces tick latency."
            else -> "Max Level! Combine with Spinach to summon the Soul Eater."
        }
        WeaponType.SOUL_EATER -> "Godtier evolved! Large purple vacuum vortex. Lifesteals health while ticking dark damage."
        WeaponType.AXE -> when (lvl) {
            1 -> "Throws broad axes high upwards that pierce and crash downwards."
            2 -> "+1 Axe projectile count bonus."
            3 -> "+35% damage. Adds +1 pierce durability."
            4 -> "+25% size of falling iron axes."
            else -> "Max Level! Combine with Pegasus Wings to summon Blood Axe meteors."
        }
        WeaponType.BLOOD_AXE -> "Armageddon evolved! Spawns massive cascading orange fireballs targeting clusters."
    }

    private fun getPassiveLvlDescription(pt: PassiveType, lvl: Int) = when (pt) {
        PassiveType.CROWN -> "+${lvl * 15}% extra experience point gains."
        PassiveType.HOLLOW_HEART -> "+${lvl * 20} bonus limit to Max HP."
        PassiveType.SPINACH -> "+${lvl * 15}% massive damage output multiplier."
        PassiveType.WINGS -> "+${lvl * 15}% movement speed increase."
        PassiveType.DUPLICATOR -> "Shoots +$lvl extra projectiles on all active weapons!"
        PassiveType.ATTRACTOR -> "+${lvl * 35}% item attraction range."
    }

    private fun getWeaponRarity(wt: WeaponType, lvl: Int) = when {
        lvl == 5 -> UpgradeRarity.EPIC
        lvl >= 3 -> UpgradeRarity.RARE
        else -> UpgradeRarity.COMMON
    }

    private fun getPassiveRarity(pt: PassiveType, lvl: Int) = when {
        lvl == 5 -> UpgradeRarity.EPIC
        lvl >= 3 -> UpgradeRarity.RARE
        else -> UpgradeRarity.COMMON
    }
}
