package com.orlikalabs.microreview

import android.content.Context
import androidx.annotation.StringRes

/** The texts of the lifestyle side: built-in habits' names, the achievements, the ranks ([Habits], [Progress]). */
object LifeText {
    fun habitName(context: Context, habit: Habits.Habit): String =
        habit.name.ifEmpty { builtIn(habit.id)?.let { context.getString(it) } ?: habit.id }

    @StringRes
    private fun builtIn(id: String): Int? = when (id) {
        "bed" -> R.string.habit_bed
        "breathe" -> R.string.habit_breathe
        "creatine" -> R.string.habit_creatine
        "floss" -> R.string.habit_floss
        "journal" -> R.string.habit_journal
        "magnesium" -> R.string.habit_magnesium
        "meals" -> R.string.habit_meals
        "multivitamin" -> R.string.habit_multivitamin
        "omega_3" -> R.string.habit_omega_3
        "protein" -> R.string.habit_protein
        "read" -> R.string.habit_read
        "sunlight" -> R.string.habit_sunlight
        "tidy" -> R.string.habit_tidy
        "vitamin_d" -> R.string.habit_vitamin_d
        "water" -> R.string.habit_water
        else -> null
    }

    @StringRes
    fun achievement(id: String): Int = when (id) {
        "first_step" -> R.string.ach_first_step
        "cards_100" -> R.string.ach_cards_100
        "cards_1000" -> R.string.ach_cards_1000
        "cups_100" -> R.string.ach_cups_100
        "water_week" -> R.string.ach_water_week
        "evening_week" -> R.string.ach_evening_week
        "evening_month" -> R.string.ach_evening_month
        "tasks_10" -> R.string.ach_tasks_10
        "tasks_100" -> R.string.ach_tasks_100
        "spells_10" -> R.string.ach_spells_10
        "stand_50" -> R.string.ach_stand_50
        "bedtime_7" -> R.string.ach_bedtime_7
        "perfect_day" -> R.string.ach_perfect_day
        "perfect_week" -> R.string.ach_perfect_week
        "ikkyu" -> R.string.ach_ikkyu
        "shodan" -> R.string.ach_shodan
        else -> R.string.life_achievements
    }

    @StringRes
    fun achievementDetail(id: String): Int = when (id) {
        "first_step" -> R.string.ach_first_step_detail
        "cards_100" -> R.string.ach_cards_100_detail
        "cards_1000" -> R.string.ach_cards_1000_detail
        "cups_100" -> R.string.ach_cups_100_detail
        "water_week" -> R.string.ach_water_week_detail
        "evening_week" -> R.string.ach_evening_week_detail
        "evening_month" -> R.string.ach_evening_month_detail
        "tasks_10" -> R.string.ach_tasks_10_detail
        "tasks_100" -> R.string.ach_tasks_100_detail
        "spells_10" -> R.string.ach_spells_10_detail
        "stand_50" -> R.string.ach_stand_50_detail
        "bedtime_7" -> R.string.ach_bedtime_7_detail
        "perfect_day" -> R.string.ach_perfect_day_detail
        "perfect_week" -> R.string.ach_perfect_week_detail
        "ikkyu" -> R.string.ach_ikkyu_detail
        "shodan" -> R.string.ach_shodan_detail
        else -> R.string.life_achievements
    }

    fun rankName(context: Context, rank: Progress.Rank): String = when {
        !rank.isDan -> context.getString(R.string.rank_kyu, rank.number)
        rank.number == 1 -> context.getString(R.string.rank_shodan)
        else -> context.getString(R.string.rank_dan, rank.number)
    }
}
