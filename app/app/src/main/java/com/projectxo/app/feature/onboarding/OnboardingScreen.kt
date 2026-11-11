package com.projectxo.app.feature.onboarding

import com.projectxo.app.core.ui.icons.ModuleUi
import com.projectxo.app.core.ui.format.formatVolume
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectxo.app.R
import com.projectxo.app.core.model.Activity
import com.projectxo.app.core.model.HeightUnit
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.Sex
import com.projectxo.app.core.model.TextSize
import com.projectxo.app.core.model.ThemeChoice
import com.projectxo.app.core.model.WeightGoal
import com.projectxo.app.core.model.WeightUnit
import com.projectxo.app.core.plan.BmiCategory
import com.projectxo.app.core.plan.PlanCalculator
import com.projectxo.app.core.plan.UnitMath
import com.projectxo.app.core.ui.components.XoHairline
import com.projectxo.app.core.ui.components.XoOption
import com.projectxo.app.core.ui.components.XoPrimaryButton
import com.projectxo.app.core.ui.components.XoSegmented
import com.projectxo.app.core.ui.components.XoStepper
import com.projectxo.app.core.ui.components.XoSwitchRow
import com.projectxo.app.core.ui.components.XoTextButton
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.AcidXoColors
import com.projectxo.app.core.ui.theme.BloodXoColors
import com.projectxo.app.core.ui.theme.LightXoColors
import com.projectxo.app.core.ui.theme.VoltXoColors
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoRadius
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Первый запуск (design/onboarding.html, Р29, Р51). Тексты — только из ресурсов. */
@Composable
fun OnboardingScreen(vm: OnboardingViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val app by vm.appSettings.collectAsStateWithLifecycle()
    val reduced = LocalReducedMotion.current
    val c = XoTheme.colors

    BackHandler(enabled = s.policyOpen || s.step != OnbStep.Privacy) {
        if (s.policyOpen) vm.setPolicyOpen(false) else vm.back()
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(s.step, onBack = vm::back)
            AnimatedContent(
                targetState = s.step,
                transitionSpec = {
                    val dur = if (reduced) 1 else XoMotion.STATE_MS + 40
                    val shift = if (reduced) 0 else 24
                    (fadeIn(tween(dur, easing = XoMotion.Standard)) +
                        slideInHorizontally(tween(dur, easing = XoMotion.Standard)) { if (s.forward) shift * 3 else -shift * 3 }) togetherWith
                        fadeOut(tween(dur / 2))
                },
                label = "onboarding",
                modifier = Modifier.weight(1f),
            ) { step ->
                when (step) {
                    OnbStep.Privacy -> PrivacyStep(onFull = { vm.setPolicyOpen(true) }, onNext = vm::next)
                    OnbStep.Network -> NetworkStep(s.usesVpn, vm::setUsesVpn, vm::next)
                    OnbStep.Comfort -> ComfortStep(
                        textSize = app.textSize, theme = app.theme, contrast = app.highContrast, motion = app.reduceMotion,
                        onText = { t -> vm.updateAppearance { it.copy(textSize = t) } },
                        onTheme = { t -> vm.updateAppearance { it.copy(theme = t) } },
                        onContrast = { v -> vm.updateAppearance { it.copy(highContrast = v) } },
                        onMotion = { v -> vm.updateAppearance { it.copy(reduceMotion = v) } },
                        onNext = vm::next,
                    )
                    OnbStep.Modules -> ModulesStep(s.modules, vm::toggleModule, vm::next)
                    OnbStep.About -> AboutStep(s, vm)
                    OnbStep.Activity -> ActivityStep(s.activity, vm::setActivity, vm::next)
                    OnbStep.Goal -> GoalStep(s, vm::setGoal, vm::next)
                    OnbStep.Reminders -> RemindersStep(s, vm::setReminders, vm::next)
                    OnbStep.Calc -> CalcStep(s, onDone = vm::next)
                    OnbStep.Plan -> PlanStep(s, onAccept = vm::finish)
                }
            }
        }
        PolicySheet(open = s.policyOpen, onClose = { vm.setPolicyOpen(false) })
    }
}

/** Полоса шагов сверху и «Назад». На экране расчёта и плана полосы нет. */
@Composable
private fun TopBar(step: OnbStep, onBack: () -> Unit) {
    val c = XoTheme.colors
    val steps = OnbStep.entries.filter { it != OnbStep.Calc && it != OnbStep.Plan }
    val pos = steps.indexOf(step)
    Column(Modifier.padding(horizontal = XoSpacing.screenGutter).padding(top = XoSpacing.s3)) {
        if (pos >= 0) {
            val a11y = stringResource(R.string.onb_step_a11y, pos + 1, steps.size)
            Row(Modifier.fillMaxWidth().height(2.dp).semantics { contentDescription = a11y }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.forEachIndexed { i, _ ->
                    val fill by animateFloatAsState(if (i <= pos) 1f else 0f, tween(420, easing = XoMotion.Standard), label = "seg")
                    Box(Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(c.track)) {
                        Box(Modifier.fillMaxWidth(fill).height(2.dp).background(c.ink))
                    }
                }
            }
        }
        Box(Modifier.height(XoSize.touchMin)) {
            if (step != OnbStep.Privacy && step != OnbStep.Calc) {
                Box(
                    Modifier.size(XoSize.touchMin).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.CenterStart,
                ) { Icon(XoIcons.Back, stringResource(R.string.back), tint = c.ink, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

/** Каркас шага: заголовок, подпись, прокручиваемое тело и низ с кнопкой. */
@Composable
private fun StepScaffold(
    @StringRes title: Int,
    lead: String?,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = XoTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = XoSpacing.screenGutter)) {
        Text(stringResource(title), style = XoTheme.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
        if (lead != null) {
            Spacer(Modifier.height(XoSpacing.s2))
            Text(lead, style = XoTheme.type.body, color = c.muted)
        }
        Spacer(Modifier.height(XoSpacing.s5))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
        Column(Modifier.padding(top = XoSpacing.s3, bottom = XoSpacing.s4), verticalArrangement = Arrangement.spacedBy(XoSpacing.s2), content = footer)
    }
}

@Composable
private fun PrivacyStep(onFull: () -> Unit, onNext: () -> Unit) {
    StepScaffold(R.string.onb_privacy_title, stringResource(R.string.onb_privacy_lead), footer = {
        XoPrimaryButton(stringResource(R.string.onb_privacy_accept), onNext)
    }) {
        Point(XoIcons.Phone, R.string.onb_privacy_1_t, R.string.onb_privacy_1_s)
        Point(XoIcons.Shield, R.string.onb_privacy_2_t, R.string.onb_privacy_2_s)
        Point(XoIcons.Trash, R.string.onb_privacy_3_t, R.string.onb_privacy_3_s)
        XoHairline()
        XoTextButton(stringResource(R.string.onb_privacy_full), onFull)
    }
}

/** Р81: «Вы пользуетесь VPN?»: от ответа зависит, каким путём уйдёт снимок в «Еде по фото». */
@Composable
private fun NetworkStep(usesVpn: Boolean?, onSelect: (Boolean) -> Unit, onNext: () -> Unit) {
    val c = XoTheme.colors
    StepScaffold(R.string.onb_net_title, stringResource(R.string.onb_net_lead), footer = {
        XoPrimaryButton(stringResource(R.string.next), onNext, enabled = usesVpn != null)
    }) {
        XoOption(stringResource(R.string.onb_net_no), stringResource(R.string.onb_net_no_sub), usesVpn == false, { onSelect(false) })
        XoOption(stringResource(R.string.onb_net_yes), stringResource(R.string.onb_net_yes_sub), usesVpn == true, { onSelect(true) })
        Spacer(Modifier.height(XoSpacing.s4))
        XoHairline()
        Spacer(Modifier.height(XoSpacing.s3))
        Text(stringResource(R.string.onb_net_why_t), style = XoTheme.type.bodyStrong, color = c.ink)
        Spacer(Modifier.height(XoSpacing.textGap))
        Text(stringResource(R.string.onb_net_why), style = XoTheme.type.caption, color = c.muted)
    }
}

@Composable
private fun Point(icon: ImageVector, @StringRes t: Int, @StringRes s: Int) {
    val c = XoTheme.colors
    XoHairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Icon(icon, null, tint = c.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(t), style = XoTheme.type.bodyStrong, color = c.ink)
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(stringResource(s), style = XoTheme.type.caption, color = c.muted)
        }
    }
}

@Composable
internal fun PolicySheet(open: Boolean, onClose: () -> Unit) {
    val c = XoTheme.colors
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(open, enter = fadeIn(tween(if (reduced) 1 else 200)), exit = fadeOut(tween(if (reduced) 1 else 200))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClose))
    }
    AnimatedVisibility(
        open,
        enter = slideInVertically(tween(if (reduced) 1 else 360, easing = XoMotion.Standard)) { it },
        exit = slideOutVertically(tween(if (reduced) 1 else 280)) { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.86f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(c.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = XoSpacing.screenGutter),
            ) {
                Spacer(Modifier.height(XoSpacing.s4))
                Text(stringResource(R.string.policy_title), style = XoTheme.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    listOf(
                        R.string.policy_h1 to R.string.policy_p1, R.string.policy_h2 to R.string.policy_p2,
                        R.string.policy_h3 to R.string.policy_p3, R.string.policy_h4 to R.string.policy_p4,
                        R.string.policy_h5 to R.string.policy_p5, R.string.policy_h6 to R.string.policy_p6,
                        R.string.policy_h7 to R.string.policy_p7, R.string.policy_h8 to R.string.policy_p8,
                    ).forEach { (h, p) ->
                        Spacer(Modifier.height(XoSpacing.s4))
                        Text(stringResource(h), style = XoTheme.type.bodyStrong, color = c.ink)
                        Text(stringResource(p), style = XoTheme.type.body, color = c.muted)
                    }
                }
                Spacer(Modifier.height(XoSpacing.s3))
                XoPrimaryButton(stringResource(R.string.got_it), onClose)
                Spacer(Modifier.height(XoSpacing.s4))
            }
        }
    }
}

@Composable
private fun ComfortStep(
    textSize: TextSize, theme: ThemeChoice, contrast: Boolean, motion: Boolean,
    onText: (TextSize) -> Unit, onTheme: (ThemeChoice) -> Unit, onContrast: (Boolean) -> Unit, onMotion: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    val c = XoTheme.colors
    StepScaffold(R.string.onb_comfort_title, stringResource(R.string.onb_comfort_lead), footer = {
        XoPrimaryButton(stringResource(R.string.next), onNext)
    }) {
        // Образец: на нём сразу видно размер текста, контраст и тему.
        Column(Modifier.fillMaxWidth().border(XoSize.hairline, c.line, RoundedCornerShape(18.dp)).padding(XoSpacing.s4)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatCount(1450), style = XoTheme.type.displayM, color = c.ink)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.unit_ml), style = XoTheme.type.displayUnitM, color = c.muted)
            }
            Text(stringResource(R.string.onb_preview_caption), style = XoTheme.type.caption, color = c.muted)
            Spacer(Modifier.height(XoSpacing.s3))
            Box(Modifier.fillMaxWidth().height(3.dp).background(c.track)) { Box(Modifier.fillMaxWidth(0.63f).height(3.dp).background(c.accent)) }
        }
        Spacer(Modifier.height(XoSpacing.s5))
        Label(R.string.onb_text_size)
        XoSegmented(
            listOf(TextSize.Small to stringResource(R.string.text_size_small), TextSize.Normal to stringResource(R.string.text_size_normal),
                TextSize.Large to stringResource(R.string.text_size_large), TextSize.Huge to stringResource(R.string.text_size_huge)),
            textSize, onText,
        )
        Spacer(Modifier.height(XoSpacing.s5))
        Label(R.string.onb_theme)
        ThemePicker(theme, onTheme)
        Spacer(Modifier.height(XoSpacing.s4))
        XoHairline()
        XoSwitchRow(stringResource(R.string.onb_contrast), stringResource(R.string.onb_contrast_sub), contrast, onContrast)
        XoHairline()
        XoSwitchRow(stringResource(R.string.onb_motion), stringResource(R.string.onb_motion_sub), motion, onMotion)
        XoHairline()
    }
}

@Composable
private fun Label(@StringRes id: Int, hint: String? = null) {
    val c = XoTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = XoSpacing.s2), verticalAlignment = Alignment.Bottom) {
        Text(stringResource(id), style = XoTheme.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, style = XoTheme.type.caption, color = c.muted)
    }
}

/** Четыре темы образцами (Р49). Образец рисуется цветами своей темы, а не текущей. */
@Composable
fun ThemePicker(selected: ThemeChoice, onSelect: (ThemeChoice) -> Unit) {
    val c = XoTheme.colors
    // Сетка 2 × 2: подписи тем не переносятся по буквам даже при крупном тексте.
    val all = listOf(
        ThemeChoice.Warm to R.string.theme_warm, ThemeChoice.Acid to R.string.theme_acid,
        ThemeChoice.Volt to R.string.theme_volt, ThemeChoice.Blood to R.string.theme_blood,
    )
    Column(verticalArrangement = Arrangement.spacedBy(XoSpacing.s2)) { all.chunked(2).forEach { pair ->
    Row(horizontalArrangement = Arrangement.spacedBy(XoSpacing.s2)) {
        pair.forEach { (t, label) ->
            val pal = when (t) {
                ThemeChoice.Warm -> LightXoColors; ThemeChoice.Acid -> AcidXoColors
                ThemeChoice.Volt -> VoltXoColors; ThemeChoice.Blood -> BloodXoColors
            }
            val on = t == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(pal.background)
                    .border(if (on) 2.dp else XoSize.hairline, if (on) c.ink else c.line, RoundedCornerShape(14.dp))
                    .clickable(role = Role.RadioButton, onClick = { onSelect(t) })
                    .padding(8.dp),
            ) {
                Box(Modifier.align(Alignment.TopEnd).size(18.dp).border(3.dp, pal.accent, CircleShape))
                Text(stringResource(label), style = XoTheme.type.caption, color = pal.ink, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart))
            }
        }
    }
    } }
}

@Composable
private fun ModulesStep(modules: Set<Module>, onToggle: (Module) -> Unit, onNext: () -> Unit) {
    val c = XoTheme.colors
    StepScaffold(R.string.onb_modules_title, stringResource(R.string.onb_modules_lead), footer = {
        XoPrimaryButton(stringResource(R.string.next), onNext, enabled = modules.isNotEmpty())
    }) {
        Module.entries.forEach { m ->
            XoHairline()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ModuleUi.icon(m), null, tint = c.ink, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(XoSpacing.s3))
                XoSwitchRow(stringResource(ModuleUi.nameRes(m)), stringResource(ModuleUi.subRes(m)), m in modules, { onToggle(m) }, Modifier.weight(1f))
            }
        }
        XoHairline()
        Spacer(Modifier.height(XoSpacing.s3))
        Text(stringResource(R.string.onb_modules_need_one), style = XoTheme.type.caption, color = c.muted)
    }
}

@Composable
private fun bmiLabel(cat: BmiCategory) = stringResource(
    when (cat) {
        BmiCategory.Under -> R.string.bmi_under; BmiCategory.Normal -> R.string.bmi_normal; BmiCategory.Over -> R.string.bmi_over
        BmiCategory.Obese1 -> R.string.bmi_obese1; BmiCategory.Obese2 -> R.string.bmi_obese2; BmiCategory.Obese3 -> R.string.bmi_obese3
    },
)

private fun dec(locale: Locale, x: Double, digits: Int = 1) = String.format(locale, "%.${digits}f", x)

@Composable
private fun AboutStep(s: OnbState, vm: OnboardingViewModel) {
    val c = XoTheme.colors
    val locale = rememberFormatLocale()
    StepScaffold(R.string.onb_about_title, stringResource(R.string.onb_about_lead), footer = {
        XoPrimaryButton(stringResource(R.string.next), vm::next)
    }) {
        Label(R.string.onb_sex, stringResource(R.string.onb_sex_hint))
        XoSegmented(
            listOf(Sex.Male to stringResource(R.string.sex_male), Sex.Female to stringResource(R.string.sex_female), Sex.Unspecified to stringResource(R.string.sex_unspecified)),
            s.sex, vm::setSex,
        )
        Spacer(Modifier.height(XoSpacing.s5))
        val less = stringResource(R.string.less)
        val more = stringResource(R.string.more)

        Label(R.string.onb_age, stringResource(R.string.onb_age_hint))
        val years = stringResource(R.string.unit_years)
        XoStepper(s.age.toString(), years, stringResource(R.string.stepper_a11y, stringResource(R.string.onb_age), s.age, years), less, more, { vm.stepAge(-1) }, { vm.stepAge(1) })
        XoHairline()
        Spacer(Modifier.height(XoSpacing.s4))

        Label(R.string.onb_height, stringResource(R.string.onb_bmi_line, dec(locale, s.bmi), bmiLabel(PlanCalculator.category(s.bmi))))
        XoSegmented(listOf(HeightUnit.Cm to stringResource(R.string.unit_cm), HeightUnit.FtIn to stringResource(R.string.unit_ft)), s.units.height, { vm.setUnits(s.units.copy(height = it)) })
        val (hText, hUnit) = if (s.units.height == HeightUnit.Cm) s.heightCm.toString() to stringResource(R.string.unit_cm)
        else UnitMath.cmToFtIn(s.heightCm).let { (ft, inch) -> "$ft′ $inch″" to "" }
        XoStepper(hText, hUnit, stringResource(R.string.stepper_a11y, stringResource(R.string.onb_height), hText, hUnit), less, more, { vm.stepHeight(-1) }, { vm.stepHeight(1) })
        XoHairline()
        Spacer(Modifier.height(XoSpacing.s4))

        Label(R.string.onb_weight)
        XoSegmented(listOf(WeightUnit.Kg to stringResource(R.string.unit_kg), WeightUnit.Lb to stringResource(R.string.unit_lb)), s.units.weight, { vm.setUnits(s.units.copy(weight = it)) })
        val (wText, wUnit) = if (s.units.weight == WeightUnit.Kg) dec(locale, s.weightKg) to stringResource(R.string.unit_kg)
        else dec(locale, UnitMath.kgToLb(s.weightKg), 0) to stringResource(R.string.unit_lb)
        XoStepper(wText, wUnit, stringResource(R.string.stepper_a11y, stringResource(R.string.onb_weight), wText, wUnit), less, more, { vm.stepWeight(-1) }, { vm.stepWeight(1) })
        XoHairline()
        Spacer(Modifier.height(XoSpacing.s4))
        Label(R.string.plan_water)
        XoSegmented(
            listOf(com.projectxo.app.core.model.VolumeUnit.Ml to stringResource(R.string.unit_water_ml), com.projectxo.app.core.model.VolumeUnit.Oz to stringResource(R.string.unit_water_oz)),
            s.units.volume, { vm.setUnits(s.units.copy(volume = it)) },
        )
        Spacer(Modifier.height(XoSpacing.s3))
        Text(stringResource(R.string.onb_hold_hint), style = XoTheme.type.caption, color = c.muted)
    }
}

@Composable
private fun ActivityStep(selected: Activity, onSelect: (Activity) -> Unit, onNext: () -> Unit) {
    StepScaffold(R.string.onb_activity_title, stringResource(R.string.onb_activity_lead), footer = {
        XoPrimaryButton(stringResource(R.string.next), onNext)
    }) {
        listOf(
            Triple(Activity.None, "0", R.string.act_none to R.string.act_none_sub),
            Triple(Activity.OneTwo, "1–2", R.string.act_low to R.string.act_low_sub),
            Triple(Activity.ThreeFour, "3–4", R.string.act_mid to R.string.act_mid_sub),
            Triple(Activity.FiveSix, "5–6", R.string.act_high to R.string.act_high_sub),
            Triple(Activity.Daily, "7+", R.string.act_daily to R.string.act_daily_sub),
        ).forEach { (a, lead, texts) ->
            XoOption(stringResource(texts.first), stringResource(texts.second), a == selected, { onSelect(a) }, leading = lead)
        }
    }
}

@Composable
private fun GoalStep(s: OnbState, onSelect: (WeightGoal) -> Unit, onNext: () -> Unit) {
    val c = XoTheme.colors
    val locale = rememberFormatLocale()
    val suggested = PlanCalculator.suggestedGoal(s.bmi)
    val current = s.effectiveGoal
    val canLose = PlanCalculator.canLose(s.bmi)
    StepScaffold(
        R.string.onb_goal_title,
        stringResource(R.string.onb_goal_lead, dec(locale, s.bmi), bmiLabel(PlanCalculator.category(s.bmi))),
        footer = { XoPrimaryButton(stringResource(R.string.next), onNext) },
    ) {
        val tag = stringResource(R.string.goal_suggested)
        XoOption(stringResource(R.string.goal_lose), stringResource(if (canLose) R.string.goal_lose_sub else R.string.goal_lose_disabled),
            current == WeightGoal.Lose, { onSelect(WeightGoal.Lose) }, enabled = canLose, tag = tag.takeIf { suggested == WeightGoal.Lose })
        XoOption(stringResource(R.string.goal_keep), stringResource(R.string.goal_keep_sub), current == WeightGoal.Keep, { onSelect(WeightGoal.Keep) },
            tag = tag.takeIf { suggested == WeightGoal.Keep })
        XoOption(stringResource(R.string.goal_gain), stringResource(R.string.goal_gain_sub), current == WeightGoal.Gain, { onSelect(WeightGoal.Gain) },
            tag = tag.takeIf { suggested == WeightGoal.Gain })
        if (!canLose) {
            Spacer(Modifier.height(XoSpacing.s2))
            Text(stringResource(R.string.goal_under_note), style = XoTheme.type.caption, color = c.muted)
        }
    }
}

private fun hm(min: Int) = "%02d:%02d".format(min / 60, min % 60)

@Composable
private fun RemindersStep(s: OnbState, onChange: (com.projectxo.app.core.model.Reminders) -> Unit, onNext: () -> Unit) {
    val r = s.reminders
    StepScaffold(R.string.onb_rem_title, stringResource(R.string.onb_rem_lead), footer = {
        XoPrimaryButton(stringResource(R.string.onb_rem_cta), onNext)
    }) {
        XoHairline()
        if (Module.Water in s.modules) {
            XoSwitchRow(stringResource(R.string.rem_water), stringResource(R.string.rem_water_sub), r.water, { onChange(r.copy(water = it)) })
            XoHairline()
        }
        XoSwitchRow(stringResource(R.string.rem_summary), stringResource(R.string.rem_summary_sub), r.dailySummary, { onChange(r.copy(dailySummary = it)) })
        XoHairline()
        Column(Modifier.padding(vertical = XoSpacing.s3)) {
            Text(stringResource(R.string.rem_quiet), style = XoTheme.type.bodyStrong, color = XoTheme.colors.ink)
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(stringResource(R.string.rem_quiet_sub, hm(r.quietFromMin), hm(r.quietToMin)), style = XoTheme.type.caption, color = XoTheme.colors.muted)
        }
        XoHairline()
    }
}

/** Расчёт: столбики растут по очереди с перелётом, затем сам переход к плану (Р35, Р51). */
@Composable
private fun CalcStep(s: OnbState, onDone: () -> Unit) {
    val c = XoTheme.colors
    val reduced = LocalReducedMotion.current
    val plan = remember(s) { s.plan }
    val locale = rememberFormatLocale()
    val items = buildList {
        add(Triple(stringResource(R.string.plan_kcal), plan.kcal, 0.8f))
        if (Module.Water in s.modules) add(Triple(stringResource(R.string.plan_water), plan.waterMl, 0.62f))
        if (Module.Steps in s.modules) add(Triple(stringResource(R.string.plan_steps), plan.steps, 0.86f))
    }
    val bars = remember { List(3) { Animatable(0f) } }
    var line by rememberSaveable { mutableStateOf(0) }
    val lines = listOf(R.string.onb_calc_1, R.string.onb_calc_2, R.string.onb_calc_3, R.string.onb_calc_4)
    LaunchedEffect(Unit) {
        if (reduced) {
            bars.forEach { it.snapTo(1f) }; line = 3; delay(600); onDone(); return@LaunchedEffect
        }
        bars.forEachIndexed { i, a ->
            launch { delay(150L + i * 120L); a.animateTo(1f, XoMotion.springTouch()) }
        }
        repeat(3) { delay(650); line++ }
        delay(700)
        onDone()
    }
    val measurer = rememberTextMeasurer()
    val valueStyle = XoTheme.type.bodyStrong.copy(color = c.ink)
    val labelStyle = XoTheme.type.caption.copy(color = c.muted)
    Column(Modifier.fillMaxSize().padding(horizontal = XoSpacing.screenGutter)) {
        Text(stringResource(R.string.onb_calc_title), style = XoTheme.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(XoSpacing.s2))
        Text(stringResource(lines[line.coerceIn(0, 3)]), style = XoTheme.type.body, color = c.muted)
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = XoSpacing.s5)) {
            val n = items.size
            val bw = 56.dp.toPx()
            val gap = if (n > 1) (size.width - bw * n) / (n - 1) else 0f
            val base = size.height - 32.dp.toPx()
            val maxH = base - 40.dp.toPx()
            items.forEachIndexed { i, (label, value, h) ->
                val k = bars[i].value
                val x = if (n == 1) (size.width - bw) / 2 else i * (bw + gap)
                val bh = maxH * h * k
                drawRoundRect(if (i == 0) c.accent else c.ink, Offset(x, base - bh), Size(bw, bh.coerceAtLeast(1f)), CornerRadius(4.dp.toPx()))
                if (k > 0.02f) {
                    val shown = (value * k.coerceAtMost(1f)).toInt()
                    val t = measurer.measure(String.format(locale, "%,d", shown).replace(',', ' '), valueStyle)
                    drawText(t, topLeft = Offset(x + bw / 2 - t.size.width / 2, base - bh - t.size.height - 6.dp.toPx()))
                }
                val l = measurer.measure(label, labelStyle)
                drawText(l, topLeft = Offset(x + bw / 2 - l.size.width / 2, base + 10.dp.toPx()))
            }
            drawRect(c.line, Offset(0f, base), Size(size.width, 1.dp.toPx()))
        }
    }
}

@Composable
private fun PlanStep(s: OnbState, onAccept: () -> Unit) {
    val c = XoTheme.colors
    val locale = rememberFormatLocale()
    val plan = remember(s) { s.plan }
    var how by rememberSaveable { mutableStateOf(false) }
    StepScaffold(R.string.onb_plan_title, stringResource(R.string.onb_plan_lead), footer = {
        XoPrimaryButton(stringResource(R.string.onb_plan_accept), onAccept)
    }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(dec(locale, plan.bmi), style = XoTheme.type.displayM, color = c.ink)
            Spacer(Modifier.width(XoSpacing.s3))
            Text(bmiLabel(plan.bmiCategory).replaceFirstChar { it.titlecase(locale) }, style = XoTheme.type.bodyStrong, color = c.ink, modifier = Modifier.padding(bottom = 6.dp))
        }
        BmiScale(plan.bmi)
        Text(stringResource(R.string.onb_bmi_note), style = XoTheme.type.caption, color = c.muted)
        Spacer(Modifier.height(XoSpacing.s4))
        if (Module.Food in s.modules) {
            val pace = when (plan.goal) {
                WeightGoal.Keep -> stringResource(R.string.plan_pace_keep)
                WeightGoal.Lose -> stringResource(R.string.plan_pace_lose, dec(locale, -plan.paceKgPerWeek))
                WeightGoal.Gain -> stringResource(R.string.plan_pace_gain, dec(locale, plan.paceKgPerWeek))
            }
            PlanRow(XoIcons.Food, stringResource(R.string.plan_kcal), pace, stringResource(R.string.plan_kcal_value, formatCount(plan.kcal.toLong())),
                stringResource(R.string.plan_macros, plan.proteinG, plan.fatG, plan.carbsG))
        }
        if (Module.Water in s.modules) PlanRow(XoIcons.Water, stringResource(R.string.plan_water), null,
            formatVolume(plan.waterMl.toLong()), stringResource(R.string.plan_water_sub, formatVolume(500)))
        if (Module.Steps in s.modules) PlanRow(XoIcons.Steps, stringResource(R.string.plan_steps), null,
            stringResource(R.string.plan_steps_value, formatCount(plan.steps.toLong())), stringResource(R.string.plan_steps_sub, dec(locale, plan.steps * 0.75 / 1000)))
        XoHairline()
        Row(
            Modifier.fillMaxWidth().heightIn(min = XoSize.touchMin).clickable(role = Role.Button) { how = !how },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.plan_how), style = XoTheme.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            Text(if (how) "−" else "+", style = XoTheme.type.title, color = c.muted)
        }
        AnimatedVisibility(how) {
            Column(verticalArrangement = Arrangement.spacedBy(XoSpacing.s2)) {
                val factor = PlanCalculator.activityFactor(s.activity)
                listOf(
                    stringResource(R.string.plan_how_1, formatCount(plan.restingKcal.toLong())),
                    stringResource(R.string.plan_how_2, dec(locale, factor, 3).trimEnd('0'), formatCount(plan.dailyKcal.toLong())),
                    when (plan.goal) {
                        WeightGoal.Keep -> stringResource(R.string.plan_how_keep)
                        WeightGoal.Gain -> stringResource(R.string.plan_how_gain)
                        WeightGoal.Lose -> stringResource(R.string.plan_how_lose, Math.round((1 - plan.kcal.toDouble() / plan.dailyKcal) * 100).toInt().coerceAtLeast(0), formatCount(plan.floorKcal.toLong()))
                    },
                    stringResource(R.string.plan_how_4, dec(locale, plan.proteinPerKg), dec(locale, plan.referenceWeightKg)),
                    stringResource(R.string.plan_how_5),
                    stringResource(R.string.plan_how_6),
                    stringResource(R.string.plan_how_7),
                ).forEach { Text(it, style = XoTheme.type.caption, color = c.muted) }
                Spacer(Modifier.height(XoSpacing.s3))
            }
        }
    }
}

@Composable
private fun PlanRow(icon: ImageVector, title: String, sub2: String?, value: String, sub: String) {
    val c = XoTheme.colors
    XoHairline()
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Icon(icon, null, tint = c.ink, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(XoSpacing.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = XoTheme.type.body, color = c.ink)
            if (sub2 != null) Text(sub2, style = XoTheme.type.caption, color = c.muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = XoTheme.type.subtitle, color = c.ink)
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(sub, style = XoTheme.type.caption, color = c.muted)
        }
    }
}

/** Шкала ИМТ: 6 отрезков ВОЗ, текущий — ink, треугольник едет к значению. */
@Composable
private fun BmiScale(bmi: Double) {
    val c = XoTheme.colors
    val reduced = LocalReducedMotion.current
    val lo = 15.0; val hi = 42.0
    val target = ((bmi.coerceIn(lo, hi) - lo) / (hi - lo)).toFloat()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { if (reduced) anim.snapTo(target) else anim.animateTo(target, tween(600, easing = XoMotion.Standard)) }
    val measurer = rememberTextMeasurer()
    val labelStyle = XoTheme.type.label.copy(color = c.muted)
    Canvas(Modifier.fillMaxWidth().height(40.dp).padding(vertical = 4.dp)) {
        val cuts = listOf(lo, 18.5, 25.0, 30.0, 35.0, 40.0, hi)
        val y = 10.dp.toPx(); val h = 6.dp.toPx()
        fun xOf(v: Double) = ((v - lo) / (hi - lo)).toFloat() * size.width
        for (i in 0 until cuts.size - 1) {
            val a = xOf(cuts[i]) + 1.dp.toPx(); val b = xOf(cuts[i + 1]) - 1.dp.toPx()
            val on = bmi >= cuts[i] && (bmi < cuts[i + 1] || i == cuts.size - 2)
            drawRoundRect(if (on) c.ink else c.track, Offset(a, y), Size(b - a, h), CornerRadius(h / 2))
        }
        cuts.subList(1, cuts.size - 1).forEach { v ->
            val t = measurer.measure(if (v % 1.0 == 0.0) v.toInt().toString() else "18,5", labelStyle)
            drawText(t, topLeft = Offset(xOf(v) - t.size.width / 2, y + h + 4.dp.toPx()))
        }
        val mx = anim.value * size.width
        val tri = Path().apply { moveTo(mx - 6.dp.toPx(), 0f); lineTo(mx + 6.dp.toPx(), 0f); lineTo(mx, 7.dp.toPx()); close() }
        drawPath(tri, c.ink)
    }
}
