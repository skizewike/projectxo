package com.projectxo.app.feature.workout

import androidx.annotation.StringRes
import com.projectxo.app.R
import com.projectxo.app.core.model.Exercise
import com.projectxo.app.core.model.ExerciseGroup

/** Тексты каталога упражнений: названия, подсказки по технике, названия групп (Р75). */
object ExerciseText {
    @StringRes
    fun nameRes(e: Exercise): Int = when (e) {
        Exercise.Pushups -> R.string.ex_pushups
        Exercise.Dips -> R.string.ex_dips
        Exercise.Pullups -> R.string.ex_pullups
        Exercise.Superman -> R.string.ex_superman
        Exercise.Hinge -> R.string.ex_hinge
        Exercise.Squats -> R.string.ex_squats
        Exercise.JumpSquat -> R.string.ex_jump_squat
        Exercise.Lunges -> R.string.ex_lunges
        Exercise.StepUp -> R.string.ex_step_up
        Exercise.Bridge -> R.string.ex_bridge
        Exercise.CalfRaise -> R.string.ex_calf_raise
        Exercise.WallSit -> R.string.ex_wall_sit
        Exercise.DonkeyKick -> R.string.ex_donkey_kick
        Exercise.Crunches -> R.string.ex_crunches
        Exercise.Bicycle -> R.string.ex_bicycle
        Exercise.LegRaise -> R.string.ex_leg_raise
        Exercise.Flutter -> R.string.ex_flutter
        Exercise.Plank -> R.string.ex_plank
        Exercise.SidePlank -> R.string.ex_side_plank
        Exercise.BirdDog -> R.string.ex_bird_dog
        Exercise.Burpee -> R.string.ex_burpee
        Exercise.Jacks -> R.string.ex_jacks
        Exercise.Mountain -> R.string.ex_mountain
        Exercise.HighKnees -> R.string.ex_high_knees
    }

    @StringRes
    fun hintRes(e: Exercise): Int = when (e) {
        Exercise.Pushups -> R.string.ex_hint_pushups
        Exercise.Dips -> R.string.ex_hint_dips
        Exercise.Pullups -> R.string.ex_hint_pullups
        Exercise.Superman -> R.string.ex_hint_superman
        Exercise.Hinge -> R.string.ex_hint_hinge
        Exercise.Squats -> R.string.ex_hint_squats
        Exercise.JumpSquat -> R.string.ex_hint_jump_squat
        Exercise.Lunges -> R.string.ex_hint_lunges
        Exercise.StepUp -> R.string.ex_hint_step_up
        Exercise.Bridge -> R.string.ex_hint_bridge
        Exercise.CalfRaise -> R.string.ex_hint_calf_raise
        Exercise.WallSit -> R.string.ex_hint_wall_sit
        Exercise.DonkeyKick -> R.string.ex_hint_donkey_kick
        Exercise.Crunches -> R.string.ex_hint_crunches
        Exercise.Bicycle -> R.string.ex_hint_bicycle
        Exercise.LegRaise -> R.string.ex_hint_leg_raise
        Exercise.Flutter -> R.string.ex_hint_flutter
        Exercise.Plank -> R.string.ex_hint_plank
        Exercise.SidePlank -> R.string.ex_hint_side_plank
        Exercise.BirdDog -> R.string.ex_hint_bird_dog
        Exercise.Burpee -> R.string.ex_hint_burpee
        Exercise.Jacks -> R.string.ex_hint_jacks
        Exercise.Mountain -> R.string.ex_hint_mountain
        Exercise.HighKnees -> R.string.ex_hint_high_knees
    }

    @StringRes
    fun groupRes(g: ExerciseGroup): Int = when (g) {
        ExerciseGroup.ChestArms -> R.string.ex_group_chest_arms
        ExerciseGroup.Back -> R.string.ex_group_back
        ExerciseGroup.Legs -> R.string.ex_group_legs
        ExerciseGroup.Core -> R.string.ex_group_core
        ExerciseGroup.FullBody -> R.string.ex_group_full_body
    }
}
