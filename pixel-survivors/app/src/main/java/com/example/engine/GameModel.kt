package com.example.engine

import androidx.compose.ui.graphics.Color

enum class WeaponType {
    MAGIC_WAND,
    SCYTHE,
    GARLIC,
    AXE,
    // Evolved weapons
    HOLY_SCEPTER,  // evolved from Magic Wand
    DEATH_SCYTHE,  // evolved from Scythe
    SOUL_EATER,    // evolved from Garlic
    BLOOD_AXE      // evolved from Axe
}

enum class PassiveType {
    CROWN,        // XP gain
    HOLLOW_HEART, // Max HP
    SPINACH,      // Might (+damage)
    WINGS,        // Move speed
    DUPLICATOR,   // Proj quantity
    ATTRACTOR     // Collection range
}

data class CharacterConfig(
    val id: String,
    val name: String,
    val startingWeapon: WeaponType,
    val baseMaxHp: Float,
    val baseSpeed: Float,
    val baseDamageMultiplier: Float,
    val description: String,
    val unlockCost: Int,
    val normalColor: Color,
    val neonColor: Color
) {
    companion object {
        val ALL = listOf(
            CharacterConfig(
                id = "antonio",
                name = "Antonio (Knight)",
                startingWeapon = WeaponType.AXE,
                baseMaxHp = 120f,
                baseSpeed = 4.2f,
                baseDamageMultiplier = 1.15f,
                description = "Sturdy combat veteran. Highest maximum health with high base damage.",
                unlockCost = 0,
                normalColor = Color(0xFF90A4AE),
                neonColor = Color(0xFFCFD8DC)
            ),
            CharacterConfig(
                id = "imelda",
                name = "Imelda (Mage)",
                startingWeapon = WeaponType.MAGIC_WAND,
                baseMaxHp = 80f,
                baseSpeed = 4.8f,
                baseDamageMultiplier = 1.0f,
                description = "Brilliant arcane adept. Fast movement speed and low attack cooldowns.",
                unlockCost = 0,
                normalColor = Color(0xFF64B5F6),
                neonColor = Color(0xFF80DEEA)
            ),
            CharacterConfig(
                id = "cavallo",
                name = "Yatta Cavallo",
                startingWeapon = WeaponType.SCYTHE,
                baseMaxHp = 95f,
                baseSpeed = 5.2f,
                baseDamageMultiplier = 0.95f,
                description = "Wind-walking ranger. Shoots extra projectiles simultaneously.",
                unlockCost = 400,
                normalColor = Color(0xFFFFF176),
                neonColor = Color(0xFFFFB74D)
            ),
            CharacterConfig(
                id = "poe",
                name = "Old Poe",
                startingWeapon = WeaponType.GARLIC,
                baseMaxHp = 70f,
                baseSpeed = 3.9f,
                baseDamageMultiplier = 1.3f,
                description = "Adept master of high-frequency aura. Starts with garlic shield.",
                unlockCost = 800,
                normalColor = Color(0xFF81C784),
                neonColor = Color(0xFFAED581)
            )
        )
    }
}

enum class EnemyType {
    BAT,
    ZOMBIE,
    GHOST,
    WEREWOLF,
    SKELETON,
    MINI_BOSS,
    ELITE_BOSS,
    REAPER_BOSS
}

data class Enemy(
    val id: Int,
    var x: Float,
    var y: Float,
    var hp: Float,
    val maxHp: Float,
    var speed: Float,
    val damage: Float,
    val size: Float,
    val enemyType: EnemyType,
    val color: Color,
    val xpValue: Int,
    val isBoss: Boolean = false,
    val isStunned: Long = 0L // duration in ms
)

data class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float,
    val size: Float,
    val color: Color,
    val type: WeaponType,
    var pierceLeft: Int,
    var angle: Float = 0f,
    var angularSpeed: Float = 0f,
    val lifespan: Long = 4000L, // ms
    val spawnTime: Long = System.currentTimeMillis()
)

data class XPGem(
    val id: Int,
    var x: Float,
    var y: Float,
    val xpValue: Int,
    val isGold: Boolean,
    val amount: Int = 1,
    val color: Color,
    val size: Float,
    var isAttracted: Boolean = false
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    val maxLifespan: Float,
    var remainingLifespan: Float,
    val isNeon: Boolean = false
)

data class FloatingText(
    val text: String,
    var x: Float,
    var y: Float,
    val color: Color,
    val size: Float,
    var vy: Float,
    val maxLifespan: Float,
    var remainingLifespan: Float
)

data class UpgradeCard(
    val id: String, // e.g. "WEAPON_MAGIC_WAND" or "PASSIVE_SPINACH"
    val title: String,
    val description: String,
    val rypeLabel: String, // "NEW WEAPON", "LEVEL UP", "EVOLUTION", "PASSIVE ITEM"
    val rarity: UpgradeRarity,
    val isWeapon: Boolean,
    val isPassive: Boolean = false,
    val isEvolution: Boolean = false,
    val weaponType: WeaponType? = null,
    val passiveType: PassiveType? = null
)

enum class UpgradeRarity {
    COMMON,    // Gray / Greenish
    RARE,      // Blue / Teal
    EPIC,      // Purple / Violet
    LEGENDARY  // Gold / Neon Amber
}
