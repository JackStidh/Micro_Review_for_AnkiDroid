package com.orlikalabs.microreview

import androidx.annotation.StringRes

/** The texts of [MovePlan]'s exercises and parts, in `strings.xml` (move_<id>_name, _dose, _how; the guide's _what, _steps, _feel, _avoid). */
object MoveText {
    @StringRes
    fun name(move: MovePlan.Move): Int = when (move.id) {
        "press_up" -> R.string.move_press_up_name
        "bird_dog" -> R.string.move_bird_dog_name
        "side_plank_knees" -> R.string.move_side_plank_knees_name
        "curl_up" -> R.string.move_curl_up_name
        "bridge" -> R.string.move_bridge_name
        "hinge" -> R.string.move_hinge_name
        "chin_tuck" -> R.string.move_chin_tuck_name
        "scap_squeeze" -> R.string.move_scap_squeeze_name
        "pec_stretch" -> R.string.move_pec_stretch_name
        "wall_angel" -> R.string.move_wall_angel_name
        "hip_flexor" -> R.string.move_hip_flexor_name
        "hip_abduction" -> R.string.move_hip_abduction_name
        "short_foot" -> R.string.move_short_foot_name
        "standing_extension" -> R.string.move_standing_extension_name
        "walk_break" -> R.string.move_walk_break_name
        "calf_raise" -> R.string.move_calf_raise_name
        "standing_hip_flexor" -> R.string.move_standing_hip_flexor_name
        "side_plank" -> R.string.move_side_plank_name
        "push_up" -> R.string.move_push_up_name
        "box_squat" -> R.string.move_box_squat_name
        "split_squat" -> R.string.move_split_squat_name
        "row" -> R.string.move_row_name
        "single_leg_bridge" -> R.string.move_single_leg_bridge_name
        "loaded_hinge" -> R.string.move_loaded_hinge_name
        "carry" -> R.string.move_carry_name
        else -> R.string.move_title
    }

    @StringRes
    fun dose(move: MovePlan.Move): Int = when (move.id) {
        "press_up" -> R.string.move_press_up_dose
        "bird_dog" -> R.string.move_bird_dog_dose
        "side_plank_knees" -> R.string.move_side_plank_knees_dose
        "curl_up" -> R.string.move_curl_up_dose
        "bridge" -> R.string.move_bridge_dose
        "hinge" -> R.string.move_hinge_dose
        "chin_tuck" -> R.string.move_chin_tuck_dose
        "scap_squeeze" -> R.string.move_scap_squeeze_dose
        "pec_stretch" -> R.string.move_pec_stretch_dose
        "wall_angel" -> R.string.move_wall_angel_dose
        "hip_flexor" -> R.string.move_hip_flexor_dose
        "hip_abduction" -> R.string.move_hip_abduction_dose
        "short_foot" -> R.string.move_short_foot_dose
        "standing_extension" -> R.string.move_standing_extension_dose
        "walk_break" -> R.string.move_walk_break_dose
        "calf_raise" -> R.string.move_calf_raise_dose
        "standing_hip_flexor" -> R.string.move_standing_hip_flexor_dose
        "side_plank" -> R.string.move_side_plank_dose
        "push_up" -> R.string.move_push_up_dose
        "box_squat" -> R.string.move_box_squat_dose
        "split_squat" -> R.string.move_split_squat_dose
        "row" -> R.string.move_row_dose
        "single_leg_bridge" -> R.string.move_single_leg_bridge_dose
        "loaded_hinge" -> R.string.move_loaded_hinge_dose
        "carry" -> R.string.move_carry_dose
        else -> R.string.move_title
    }

    @StringRes
    fun how(move: MovePlan.Move): Int = when (move.id) {
        "press_up" -> R.string.move_press_up_how
        "bird_dog" -> R.string.move_bird_dog_how
        "side_plank_knees" -> R.string.move_side_plank_knees_how
        "curl_up" -> R.string.move_curl_up_how
        "bridge" -> R.string.move_bridge_how
        "hinge" -> R.string.move_hinge_how
        "chin_tuck" -> R.string.move_chin_tuck_how
        "scap_squeeze" -> R.string.move_scap_squeeze_how
        "pec_stretch" -> R.string.move_pec_stretch_how
        "wall_angel" -> R.string.move_wall_angel_how
        "hip_flexor" -> R.string.move_hip_flexor_how
        "hip_abduction" -> R.string.move_hip_abduction_how
        "short_foot" -> R.string.move_short_foot_how
        "standing_extension" -> R.string.move_standing_extension_how
        "walk_break" -> R.string.move_walk_break_how
        "calf_raise" -> R.string.move_calf_raise_how
        "standing_hip_flexor" -> R.string.move_standing_hip_flexor_how
        "side_plank" -> R.string.move_side_plank_how
        "push_up" -> R.string.move_push_up_how
        "box_squat" -> R.string.move_box_squat_how
        "split_squat" -> R.string.move_split_squat_how
        "row" -> R.string.move_row_how
        "single_leg_bridge" -> R.string.move_single_leg_bridge_how
        "loaded_hinge" -> R.string.move_loaded_hinge_how
        "carry" -> R.string.move_carry_how
        else -> R.string.move_title
    }

    @StringRes
    fun what(move: MovePlan.Move): Int = when (move.id) {
        "press_up" -> R.string.move_press_up_what
        "bird_dog" -> R.string.move_bird_dog_what
        "side_plank_knees" -> R.string.move_side_plank_knees_what
        "curl_up" -> R.string.move_curl_up_what
        "bridge" -> R.string.move_bridge_what
        "hinge" -> R.string.move_hinge_what
        "chin_tuck" -> R.string.move_chin_tuck_what
        "scap_squeeze" -> R.string.move_scap_squeeze_what
        "pec_stretch" -> R.string.move_pec_stretch_what
        "wall_angel" -> R.string.move_wall_angel_what
        "hip_flexor" -> R.string.move_hip_flexor_what
        "hip_abduction" -> R.string.move_hip_abduction_what
        "short_foot" -> R.string.move_short_foot_what
        "standing_extension" -> R.string.move_standing_extension_what
        "walk_break" -> R.string.move_walk_break_what
        "calf_raise" -> R.string.move_calf_raise_what
        "standing_hip_flexor" -> R.string.move_standing_hip_flexor_what
        "side_plank" -> R.string.move_side_plank_what
        "push_up" -> R.string.move_push_up_what
        "box_squat" -> R.string.move_box_squat_what
        "split_squat" -> R.string.move_split_squat_what
        "row" -> R.string.move_row_what
        "single_leg_bridge" -> R.string.move_single_leg_bridge_what
        "loaded_hinge" -> R.string.move_loaded_hinge_what
        "carry" -> R.string.move_carry_what
        else -> R.string.move_title
    }

    @StringRes
    fun steps(move: MovePlan.Move): Int = when (move.id) {
        "press_up" -> R.string.move_press_up_steps
        "bird_dog" -> R.string.move_bird_dog_steps
        "side_plank_knees" -> R.string.move_side_plank_knees_steps
        "curl_up" -> R.string.move_curl_up_steps
        "bridge" -> R.string.move_bridge_steps
        "hinge" -> R.string.move_hinge_steps
        "chin_tuck" -> R.string.move_chin_tuck_steps
        "scap_squeeze" -> R.string.move_scap_squeeze_steps
        "pec_stretch" -> R.string.move_pec_stretch_steps
        "wall_angel" -> R.string.move_wall_angel_steps
        "hip_flexor" -> R.string.move_hip_flexor_steps
        "hip_abduction" -> R.string.move_hip_abduction_steps
        "short_foot" -> R.string.move_short_foot_steps
        "standing_extension" -> R.string.move_standing_extension_steps
        "walk_break" -> R.string.move_walk_break_steps
        "calf_raise" -> R.string.move_calf_raise_steps
        "standing_hip_flexor" -> R.string.move_standing_hip_flexor_steps
        "side_plank" -> R.string.move_side_plank_steps
        "push_up" -> R.string.move_push_up_steps
        "box_squat" -> R.string.move_box_squat_steps
        "split_squat" -> R.string.move_split_squat_steps
        "row" -> R.string.move_row_steps
        "single_leg_bridge" -> R.string.move_single_leg_bridge_steps
        "loaded_hinge" -> R.string.move_loaded_hinge_steps
        "carry" -> R.string.move_carry_steps
        else -> R.string.move_title
    }

    @StringRes
    fun feel(move: MovePlan.Move): Int = when (move.id) {
        "press_up" -> R.string.move_press_up_feel
        "bird_dog" -> R.string.move_bird_dog_feel
        "side_plank_knees" -> R.string.move_side_plank_knees_feel
        "curl_up" -> R.string.move_curl_up_feel
        "bridge" -> R.string.move_bridge_feel
        "hinge" -> R.string.move_hinge_feel
        "chin_tuck" -> R.string.move_chin_tuck_feel
        "scap_squeeze" -> R.string.move_scap_squeeze_feel
        "pec_stretch" -> R.string.move_pec_stretch_feel
        "wall_angel" -> R.string.move_wall_angel_feel
        "hip_flexor" -> R.string.move_hip_flexor_feel
        "hip_abduction" -> R.string.move_hip_abduction_feel
        "short_foot" -> R.string.move_short_foot_feel
        "standing_extension" -> R.string.move_standing_extension_feel
        "walk_break" -> R.string.move_walk_break_feel
        "calf_raise" -> R.string.move_calf_raise_feel
        "standing_hip_flexor" -> R.string.move_standing_hip_flexor_feel
        "side_plank" -> R.string.move_side_plank_feel
        "push_up" -> R.string.move_push_up_feel
        "box_squat" -> R.string.move_box_squat_feel
        "split_squat" -> R.string.move_split_squat_feel
        "row" -> R.string.move_row_feel
        "single_leg_bridge" -> R.string.move_single_leg_bridge_feel
        "loaded_hinge" -> R.string.move_loaded_hinge_feel
        "carry" -> R.string.move_carry_feel
        else -> R.string.move_title
    }

    @StringRes
    fun avoid(move: MovePlan.Move): Int = when (move.id) {
        "press_up" -> R.string.move_press_up_avoid
        "bird_dog" -> R.string.move_bird_dog_avoid
        "side_plank_knees" -> R.string.move_side_plank_knees_avoid
        "curl_up" -> R.string.move_curl_up_avoid
        "bridge" -> R.string.move_bridge_avoid
        "hinge" -> R.string.move_hinge_avoid
        "chin_tuck" -> R.string.move_chin_tuck_avoid
        "scap_squeeze" -> R.string.move_scap_squeeze_avoid
        "pec_stretch" -> R.string.move_pec_stretch_avoid
        "wall_angel" -> R.string.move_wall_angel_avoid
        "hip_flexor" -> R.string.move_hip_flexor_avoid
        "hip_abduction" -> R.string.move_hip_abduction_avoid
        "short_foot" -> R.string.move_short_foot_avoid
        "standing_extension" -> R.string.move_standing_extension_avoid
        "walk_break" -> R.string.move_walk_break_avoid
        "calf_raise" -> R.string.move_calf_raise_avoid
        "standing_hip_flexor" -> R.string.move_standing_hip_flexor_avoid
        "side_plank" -> R.string.move_side_plank_avoid
        "push_up" -> R.string.move_push_up_avoid
        "box_squat" -> R.string.move_box_squat_avoid
        "split_squat" -> R.string.move_split_squat_avoid
        "row" -> R.string.move_row_avoid
        "single_leg_bridge" -> R.string.move_single_leg_bridge_avoid
        "loaded_hinge" -> R.string.move_loaded_hinge_avoid
        "carry" -> R.string.move_carry_avoid
        else -> R.string.move_title
    }

    @StringRes
    fun part(part: MovePlan.Part): Int = when (part.id) {
        "rehab" -> R.string.move_part_rehab
        "posture" -> R.string.move_part_posture
        "warmup" -> R.string.move_part_warmup
        "strength" -> R.string.move_part_strength
        else -> R.string.move_routine
    }

    @StringRes
    fun pain(spot: MovePlan.PainSpot): Int = when (spot) {
        MovePlan.PainSpot.NONE -> R.string.move_pain_none
        MovePlan.PainSpot.BACK -> R.string.move_pain_back
        MovePlan.PainSpot.BUTTOCK -> R.string.move_pain_buttock
        MovePlan.PainSpot.THIGH -> R.string.move_pain_thigh
        MovePlan.PainSpot.KNEE -> R.string.move_pain_knee
        MovePlan.PainSpot.LOWER_LEG -> R.string.move_pain_lower_leg
    }

    @StringRes
    fun trend(trend: MovePlan.Trend): Int = when (trend) {
        MovePlan.Trend.BETTER -> R.string.move_trend_better
        MovePlan.Trend.SAME -> R.string.move_trend_same
        MovePlan.Trend.WORSE -> R.string.move_trend_worse
    }

    @StringRes
    fun warning(warning: MovePlan.Warning): Int = when (warning) {
        MovePlan.Warning.BACK_OFF -> R.string.move_warning_back_off
        MovePlan.Warning.TOO_MUCH -> R.string.move_warning_too_much
    }
}
