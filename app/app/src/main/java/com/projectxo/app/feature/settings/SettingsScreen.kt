package com.projectxo.app.feature.settings

import com.projectxo.app.core.ui.icons.ModuleUi
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.model.VolumeUnit
import com.projectxo.app.core.ui.format.formatVolume
import com.projectxo.app.core.ui.format.volumeNumber
import com.projectxo.app.core.ui.format.volumeUnitLabel
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.projectxo.app.AppContainer
import com.projectxo.app.BuildConfig
import com.projectxo.app.R
import com.projectxo.app.core.model.Activity
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.HeightUnit
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.Sex
import com.projectxo.app.core.model.TextSize
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
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.formatDecimal
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import com.projectxo.app.feature.onboarding.PolicySheet
import com.projectxo.app.feature.profile.ProfileGraphsPage
import com.projectxo.app.feature.profile.ProfileViewModel
import com.projectxo.app.feature.onboarding.ThemePicker
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Страницы настроек. Внутри одного экрана: «назад» с подстраницы ведёт на главную страницу. */
private enum class Page { Main, Profile, ProfileEdit, Plan, Modules, Theme, Text, Language, Units, Notifications, FoodAi }

/** Куда ведёт «назад»: правка профиля возвращает на графики, остальное — на главную. */
private fun Page.parent(): Page = if (this == Page.ProfileEdit) Page.Profile else Page.Main

@Composable
fun SettingsEntry(container: AppContainer, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container.settings, container.goals, container::deleteAllData) } },
    )
    val settings by vm.settings.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.Main) }
    var policyOpen by rememberSaveable { mutableStateOf(false) }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    // Выгрузка CSV: система сама спрашивает, куда сохранить, разрешения не нужны.
    val context = androidx.compose.ui.platform.LocalContext.current
    val exportScope = androidx.compose.runtime.rememberCoroutineScope()
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) exportScope.launch {
            val ok = runCatching {
                val text = container.exportCsv()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
                }
            }.isSuccess
            android.widget.Toast.makeText(context, if (ok) R.string.export_done else R.string.export_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val reduced = LocalReducedMotion.current

    BackHandler(enabled = policyOpen) { policyOpen = false }
    BackHandler(enabled = !policyOpen && page != Page.Main) { page = page.parent() }
    val profileVm: ProfileViewModel = viewModel(
        key = "profile-${container.clock.today()}",
        factory = viewModelFactory { initializer { ProfileViewModel(container.history, container.dayLog, container.settings, container.clock) } },
    )

    Box(modifier.fillMaxSize().background(XoTheme.colors.background)) {
        Crossfade(page, animationSpec = tween(if (reduced) 0 else XoMotion.STATE_MS, easing = XoMotion.Standard), label = "settingsPage") { p ->
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = XoSpacing.screenGutter),
            ) {
                BackRow(
                    label = stringResource(
                        when (p) { Page.Main -> R.string.back; Page.ProfileEdit -> R.string.profile_title; else -> R.string.settings_title },
                    ),
                    onClick = if (p == Page.Main) onBack else ({ page = p.parent() }),
                )
                when (p) {
                    Page.Main -> MainPage(settings, onOpen = { page = it }, onPolicy = { policyOpen = true }, onDelete = { deleteOpen = true }, onExport = { exportLauncher.launch("xo-export-${container.clock.today()}.csv") })
                    Page.Profile -> {
                        val graphs by profileVm.state.collectAsStateWithLifecycle()
                        val period by profileVm.selectedPeriod.collectAsStateWithLifecycle()
                        ProfileGraphsPage(graphs, period, profileVm::setPeriod, onEdit = { page = Page.ProfileEdit })
                    }
                    Page.ProfileEdit -> ProfilePage(vm, settings)
                    Page.Plan -> PlanPage(vm, settings)
                    Page.Modules -> ModulesPage(vm, settings)
                    Page.Theme -> ThemePage(vm, settings)
                    Page.Text -> TextPage(vm, settings)
                    Page.Language -> LanguagePage(vm, settings)
                    Page.Units -> UnitsPage(vm, settings)
                    Page.Notifications -> NotificationsPage(vm, settings, container::showExampleReminder)
                    Page.FoodAi -> FoodAiPage(vm, settings)
                }
                Spacer(Modifier.height(XoSpacing.s7))
            }
        }
        PolicySheet(open = policyOpen, onClose = { policyOpen = false })
    }

    if (deleteOpen) {
        val colors = XoTheme.colors
        val type = XoTheme.type
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = colors.background,
            title = { Text(stringResource(R.string.delete_title), style = type.title, color = colors.ink) },
            text = { Text(stringResource(R.string.delete_body), style = type.body, color = colors.muted) },
            confirmButton = {
                TextButton(onClick = { deleteOpen = false; vm.deleteEverything() }) {
                    Text(stringResource(R.string.delete_confirm), style = type.bodyStrong, color = colors.ink)
                }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text(stringResource(R.string.cancel), style = type.body) } },
        )
    }
}

// ——— общие части ———

@Composable
private fun BackRow(label: String, onClick: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Row(
        Modifier.heightIn(min = XoSize.touchMin).clickable(role = Role.Button, onClick = onClick).padding(top = XoSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(XoIcons.Back, null, tint = colors.muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(XoSpacing.s1))
        Text(label, style = type.caption, color = colors.muted)
    }
}

@Composable
private fun PageTitle(@androidx.annotation.StringRes title: Int, @androidx.annotation.StringRes lead: Int? = null) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Text(stringResource(title), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
    if (lead != null) {
        Spacer(Modifier.height(XoSpacing.headGap))
        Text(stringResource(lead), style = type.caption, color = colors.muted)
    }
    Spacer(Modifier.height(XoSpacing.s4))
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text, style = XoTheme.type.caption, color = XoTheme.colors.muted,
        modifier = Modifier.padding(top = XoSpacing.s5, bottom = XoSpacing.s3).semantics { heading() },
    )
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    XoHairline()
    Row(
        Modifier.fillMaxWidth().heightIn(min = XoSize.rowHeight - 8.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = XoSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(XoSpacing.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.bodyStrong, color = colors.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(XoSpacing.textGap))
                Text(subtitle, style = type.caption, color = colors.muted)
            }
        }
        Spacer(Modifier.width(XoSpacing.s2))
        Icon(XoIcons.Chevron, null, tint = colors.muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Label(text: String, hint: String? = null) {
    val colors = XoTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = XoSpacing.s2), verticalAlignment = Alignment.Bottom) {
        Text(text, style = XoTheme.type.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, style = XoTheme.type.caption, color = colors.muted)
    }
}

@Composable
private fun weightLabel(kg: Double, unit: WeightUnit): String =
    if (unit == WeightUnit.Kg) "${formatDecimal(kg, 1)} ${stringResource(R.string.unit_kg)}"
    else "${formatDecimal(UnitMath.kgToLb(kg), 0)} ${stringResource(R.string.unit_lb)}"

@Composable
private fun heightLabel(cm: Int, unit: HeightUnit): String =
    if (unit == HeightUnit.Cm) "$cm ${stringResource(R.string.unit_cm)}"
    else UnitMath.cmToFtIn(cm).let { (ft, inch) -> "$ft′ $inch″" }

@Composable
private fun clock(min: Int) = "%02d:%02d".format(min / 60, min % 60)

// ——— главная страница ———

@Composable
private fun MainPage(s: AppSettings, onOpen: (Page) -> Unit, onPolicy: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit) {
    val years = stringResource(R.string.unit_years)
    val p = s.profile
    PageTitle(R.string.settings_title)

    GroupTitle(stringResource(R.string.set_group_you))
    SettingsRow(
        XoIcons.User, stringResource(R.string.set_profile),
        p?.let { stringResource(R.string.set_profile_sub, weightLabel(it.weightKg, s.units.weight), heightLabel(it.heightCm, s.units.height), it.ageYears.toString(), years) },
    ) { onOpen(Page.Profile) }
    SettingsRow(XoIcons.Target, stringResource(R.string.set_plan), stringResource(R.string.set_plan_sub)) { onOpen(Page.Plan) }
    SettingsRow(XoIcons.Grid, stringResource(R.string.set_modules), moduleSummary(s)) { onOpen(Page.Modules) }
    XoHairline()

    GroupTitle(stringResource(R.string.set_group_look))
    SettingsRow(XoIcons.Palette, stringResource(R.string.set_theme), themeName(s)) { onOpen(Page.Theme) }
    SettingsRow(XoIcons.Text, stringResource(R.string.set_text), textSizeName(s.textSize)) { onOpen(Page.Text) }
    SettingsRow(XoIcons.Globe, stringResource(R.string.set_language), languageName(s.language)) { onOpen(Page.Language) }
    SettingsRow(
        XoIcons.Ruler, stringResource(R.string.set_units),
        stringResource(
            R.string.set_units_sub,
            stringResource(if (s.units.weight == WeightUnit.Kg) R.string.unit_kg else R.string.unit_lb),
            stringResource(if (s.units.height == HeightUnit.Cm) R.string.unit_cm else R.string.unit_ft),
            volumeUnitLabel(s.units.volume),
        ),
    ) { onOpen(Page.Units) }
    XoHairline()

    GroupTitle(stringResource(R.string.set_group_notif))
    SettingsRow(
        XoIcons.Bell, stringResource(R.string.set_notif),
        stringResource(R.string.set_notif_sub, clock(s.reminders.quietFromMin), clock(s.reminders.quietToMin)),
    ) { onOpen(Page.Notifications) }
    XoHairline()

    GroupTitle(stringResource(R.string.set_group_data))
    SettingsRow(
        XoIcons.Grid, stringResource(R.string.set_foodai),
        stringResource(
            when {
                s.foodAiKey.isNotEmpty() -> R.string.set_foodai_on
                BuildConfig.FOOD_AI_PROXY_URL.isNotBlank() -> R.string.set_foodai_shared
                else -> R.string.set_foodai_off
            },
        ),
    ) { onOpen(Page.FoodAi) }
    SettingsRow(XoIcons.Doc, stringResource(R.string.set_export), stringResource(R.string.set_export_sub), onExport)
    SettingsRow(XoIcons.Trash, stringResource(R.string.set_delete), stringResource(R.string.set_delete_sub), onDelete)
    XoHairline()

    GroupTitle(stringResource(R.string.set_group_about))
    SettingsRow(XoIcons.Doc, stringResource(R.string.set_policy), stringResource(R.string.set_policy_sub), onPolicy)
    XoHairline()
    Row(Modifier.fillMaxWidth().heightIn(min = XoSize.rowHeight - 8.dp).padding(vertical = XoSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
        Icon(XoIcons.Info, null, tint = XoTheme.colors.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(XoSpacing.s3))
        Column {
            Text(stringResource(R.string.set_about), style = XoTheme.type.bodyStrong, color = XoTheme.colors.ink)
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(stringResource(R.string.set_about_sub, stringResource(R.string.contact_email), BuildConfig.VERSION_NAME), style = XoTheme.type.caption, color = XoTheme.colors.muted)
        }
    }
    XoHairline()
}

@Composable
private fun moduleSummary(s: AppSettings): String {
    val locale = rememberFormatLocale()
    return s.moduleOrder.filter { it in s.modules }.map { stringResource(com.projectxo.app.core.ui.icons.ModuleUi.nameRes(it)).lowercase(locale) }.joinToString(", ")
}

@Composable
private fun themeName(s: AppSettings): String = stringResource(
    when (s.theme) {
        com.projectxo.app.core.model.ThemeChoice.Warm -> R.string.theme_warm
        com.projectxo.app.core.model.ThemeChoice.Acid -> R.string.theme_acid
        com.projectxo.app.core.model.ThemeChoice.Volt -> R.string.theme_volt
        com.projectxo.app.core.model.ThemeChoice.Blood -> R.string.theme_blood
    },
)

@Composable
private fun textSizeName(t: TextSize): String = stringResource(
    when (t) {
        TextSize.Small -> R.string.text_size_small
        TextSize.Normal -> R.string.text_size_normal
        TextSize.Large -> R.string.text_size_large
        TextSize.Huge -> R.string.text_size_huge
    },
)

// ——— профиль ———

@Composable
private fun bmiLabel(cat: BmiCategory) = stringResource(
    when (cat) {
        BmiCategory.Under -> R.string.bmi_under; BmiCategory.Normal -> R.string.bmi_normal; BmiCategory.Over -> R.string.bmi_over
        BmiCategory.Obese1 -> R.string.bmi_obese1; BmiCategory.Obese2 -> R.string.bmi_obese2; BmiCategory.Obese3 -> R.string.bmi_obese3
    },
)

@Composable
private fun ProfilePage(vm: SettingsViewModel, s: AppSettings) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val p by vm.profile.collectAsStateWithLifecycle()
    val less = stringResource(R.string.less)
    val more = stringResource(R.string.more)
    val bmi = PlanCalculator.bmi(p.weightKg, p.heightCm)
    val days = s.startEpochDay?.let { ChronoUnit.DAYS.between(LocalDate.ofEpochDay(it), LocalDate.now()).toInt() + 1 }?.coerceAtLeast(1) ?: 1
    PageTitle(R.string.profile_title, R.string.profile_lead)

    // Три показателя в ряд.
    XoHairline()
    Row(Modifier.fillMaxWidth().padding(vertical = XoSpacing.s3)) {
        Kpi(stringResource(R.string.profile_weight), weightLabel(p.weightKg, s.units.weight), Modifier.weight(1f))
        Kpi(stringResource(R.string.profile_bmi), formatDecimal(bmi, 1), Modifier.weight(1f), bmiLabel(PlanCalculator.category(bmi)))
        Kpi(stringResource(R.string.profile_days), formatCount(days.toLong()), Modifier.weight(1f))
    }
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s5))

    Label(stringResource(R.string.onb_sex), stringResource(R.string.onb_sex_hint))
    XoSegmented(
        listOf(Sex.Male to stringResource(R.string.sex_male), Sex.Female to stringResource(R.string.sex_female), Sex.Unspecified to stringResource(R.string.sex_unspecified)),
        p.sex, vm::setSex,
    )
    Spacer(Modifier.height(XoSpacing.s5))

    val years = stringResource(R.string.unit_years)
    Label(stringResource(R.string.onb_age), stringResource(R.string.onb_age_hint))
    XoStepper(p.ageYears.toString(), years, stringResource(R.string.stepper_a11y, stringResource(R.string.onb_age), p.ageYears, years), less, more, { vm.stepAge(-1) }, { vm.stepAge(1) })
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s4))

    Label(stringResource(R.string.onb_height))
    val (hText, hUnit) = if (s.units.height == HeightUnit.Cm) p.heightCm.toString() to stringResource(R.string.unit_cm)
    else UnitMath.cmToFtIn(p.heightCm).let { (ft, inch) -> "$ft′ $inch″" to "" }
    XoStepper(hText, hUnit, stringResource(R.string.stepper_a11y, stringResource(R.string.onb_height), hText, hUnit), less, more, { vm.stepHeight(-1) }, { vm.stepHeight(1) })
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s4))

    Label(stringResource(R.string.onb_weight))
    val (wText, wUnit) = if (s.units.weight == WeightUnit.Kg) formatDecimal(p.weightKg, 1) to stringResource(R.string.unit_kg)
    else formatDecimal(UnitMath.kgToLb(p.weightKg), 0) to stringResource(R.string.unit_lb)
    XoStepper(wText, wUnit, stringResource(R.string.stepper_a11y, stringResource(R.string.onb_weight), wText, wUnit), less, more, { vm.stepWeight(-1) }, { vm.stepWeight(1) })
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s5))

    Label(stringResource(R.string.profile_activity_title))
    listOf(
        Triple(Activity.None, "0", R.string.act_none to R.string.act_none_sub),
        Triple(Activity.OneTwo, "1–2", R.string.act_low to R.string.act_low_sub),
        Triple(Activity.ThreeFour, "3–4", R.string.act_mid to R.string.act_mid_sub),
        Triple(Activity.FiveSix, "5–6", R.string.act_high to R.string.act_high_sub),
        Triple(Activity.Daily, "7+", R.string.act_daily to R.string.act_daily_sub),
    ).forEach { (a, lead, texts) ->
        XoOption(stringResource(texts.first), stringResource(texts.second), a == p.activity, { vm.setActivity(a) }, leading = lead)
    }
    Spacer(Modifier.height(XoSpacing.s4))

    Label(stringResource(R.string.profile_goal_title))
    val suggested = PlanCalculator.suggestedGoal(bmi)
    val current = (p.goal ?: suggested).let { if (it == WeightGoal.Lose && !PlanCalculator.canLose(bmi)) WeightGoal.Gain else it }
    val canLose = PlanCalculator.canLose(bmi)
    val tag = stringResource(R.string.goal_suggested)
    XoOption(stringResource(R.string.goal_lose), stringResource(if (canLose) R.string.goal_lose_sub else R.string.goal_lose_disabled),
        current == WeightGoal.Lose, { vm.setGoal(WeightGoal.Lose) }, enabled = canLose, tag = tag.takeIf { suggested == WeightGoal.Lose })
    XoOption(stringResource(R.string.goal_keep), stringResource(R.string.goal_keep_sub), current == WeightGoal.Keep, { vm.setGoal(WeightGoal.Keep) },
        tag = tag.takeIf { suggested == WeightGoal.Keep })
    XoOption(stringResource(R.string.goal_gain), stringResource(R.string.goal_gain_sub), current == WeightGoal.Gain, { vm.setGoal(WeightGoal.Gain) },
        tag = tag.takeIf { suggested == WeightGoal.Gain })
    Spacer(Modifier.height(XoSpacing.s2))
    Text(stringResource(R.string.profile_note), style = type.caption, color = colors.muted)
}

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier, sub: String? = null) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Column(modifier.padding(end = XoSpacing.s2)) {
        Text(label, style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.textGap))
        Text(value, style = type.bodyStrong, color = colors.ink)
        if (sub != null) Text(sub, style = type.caption, color = colors.muted)
    }
}

// ——— цели и план ———

@Composable
private fun PlanPage(vm: SettingsViewModel, s: AppSettings) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val st by vm.planState.collectAsStateWithLifecycle()
    var applied by rememberSaveable { mutableStateOf(false) }
    PageTitle(R.string.plan_title, R.string.plan_lead)
    val plan = st.plan
    if (plan == null) {
        Text(stringResource(R.string.plan_unavailable), style = type.body, color = colors.muted)
        return
    }
    val g = st.goals
    XoHairline()
    Row(Modifier.fillMaxWidth().padding(vertical = XoSpacing.s2)) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.plan_now), style = type.caption, color = colors.muted, modifier = Modifier.width(96.dp))
        Text(stringResource(R.string.plan_calc), style = type.caption, color = colors.muted, modifier = Modifier.width(96.dp))
    }
    val hairline = with(LocalDensity.current) { XoSize.hairline.toPx() }
    @Composable
    fun PlanRow(name: Int, now: String, calc: String) {
        Row(
            Modifier.fillMaxWidth().drawBehind { drawRect(colors.line, size = Size(size.width, hairline)) }.padding(vertical = XoSpacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(name), style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
            Text(now, style = type.caption, color = colors.muted, modifier = Modifier.width(96.dp))
            Text(calc, style = type.bodyStrong, color = colors.ink, modifier = Modifier.width(96.dp))
        }
    }
    if (Module.Food in s.modules) {
        PlanRow(R.string.plan_kcal, formatCount(g.kcal), formatCount(plan.kcal.toLong()))
        PlanRow(R.string.macro_protein, stringResource(R.string.macro_grams, formatCount(g.proteinG)), stringResource(R.string.macro_grams, formatCount(plan.proteinG.toLong())))
        PlanRow(R.string.macro_fat, stringResource(R.string.macro_grams, formatCount(g.fatG)), stringResource(R.string.macro_grams, formatCount(plan.fatG.toLong())))
        PlanRow(R.string.macro_carbs, stringResource(R.string.macro_grams, formatCount(g.carbsG)), stringResource(R.string.macro_grams, formatCount(plan.carbsG.toLong())))
    }
    if (Module.Water in s.modules) PlanRow(R.string.plan_water, formatVolume(g.waterMl), formatVolume(plan.waterMl.toLong()))
    if (Module.Steps in s.modules) PlanRow(R.string.plan_steps, formatCount(g.steps), formatCount(plan.steps.toLong()))
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s5))
    val same = st.matches
    Text(
        stringResource(if (applied && same) R.string.plan_applied else if (same) R.string.plan_same else R.string.plan_differs),
        style = type.caption, color = colors.muted,
    )
    Spacer(Modifier.height(XoSpacing.s3))
    XoPrimaryButton(stringResource(R.string.plan_apply), { vm.applyPlan(); applied = true }, enabled = !same)
    Spacer(Modifier.height(XoSpacing.s4))
    Text(stringResource(R.string.plan_how_7), style = type.caption, color = colors.muted)
}

// ——— модули, вид, единицы ———

@Composable
private fun ModulesPage(vm: SettingsViewModel, s: AppSettings) {
    val colors = XoTheme.colors
    PageTitle(R.string.modules_title, R.string.modules_lead)
    s.moduleOrder.forEachIndexed { i, m ->
        XoHairline()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ModuleUi.icon(m), null, tint = colors.ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(XoSpacing.s3))
            XoSwitchRow(stringResource(ModuleUi.nameRes(m)), stringResource(ModuleUi.subRes(m)), m in s.modules, { vm.toggleModule(m) }, Modifier.weight(1f))
            val up = stringResource(R.string.module_move_up)
            val down = stringResource(R.string.module_move_down)
            val canUp = i > 0
            val canDown = i < s.moduleOrder.lastIndex
            Box(
                Modifier.size(XoSize.touchMin).alpha(if (canUp) 1f else 0.3f).clickable(enabled = canUp, role = Role.Button) { vm.moveModule(m, -1) }.semantics { contentDescription = up },
                contentAlignment = Alignment.Center,
            ) { Icon(XoIcons.Up, null, tint = colors.ink, modifier = Modifier.size(20.dp)) }
            Box(
                Modifier.size(XoSize.touchMin).alpha(if (canDown) 1f else 0.3f).clickable(enabled = canDown, role = Role.Button) { vm.moveModule(m, 1) }.semantics { contentDescription = down },
                contentAlignment = Alignment.Center,
            ) { Icon(XoIcons.Down, null, tint = colors.ink, modifier = Modifier.size(20.dp)) }
        }
    }
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s3))
    Text(stringResource(R.string.onb_modules_need_one), style = XoTheme.type.caption, color = colors.muted)
}

@Composable
private fun ThemePage(vm: SettingsViewModel, s: AppSettings) {
    PageTitle(R.string.theme_title, R.string.theme_lead)
    ThemePicker(s.theme, vm::setTheme)
}

@Composable
private fun TextPage(vm: SettingsViewModel, s: AppSettings) {
    PageTitle(R.string.text_title)
    Label(stringResource(R.string.onb_text_size))
    XoSegmented(
        listOf(
            TextSize.Small to stringResource(R.string.text_size_small), TextSize.Normal to stringResource(R.string.text_size_normal),
            TextSize.Large to stringResource(R.string.text_size_large), TextSize.Huge to stringResource(R.string.text_size_huge),
        ),
        s.textSize, vm::setTextSize,
    )
    Spacer(Modifier.height(XoSpacing.s4))
    XoHairline()
    XoSwitchRow(stringResource(R.string.onb_contrast), stringResource(R.string.onb_contrast_sub), s.highContrast, vm::setContrast)
    XoHairline()
    XoSwitchRow(stringResource(R.string.onb_motion), stringResource(R.string.onb_motion_sub), s.reduceMotion, vm::setReduceMotion)
    XoHairline()
}

@Composable
private fun languageName(l: com.projectxo.app.core.model.AppLanguage) = stringResource(
    when (l) {
        com.projectxo.app.core.model.AppLanguage.System -> R.string.lang_system
        com.projectxo.app.core.model.AppLanguage.Ru -> R.string.lang_ru
        com.projectxo.app.core.model.AppLanguage.En -> R.string.lang_en
    },
)

@Composable
private fun LanguagePage(vm: SettingsViewModel, s: AppSettings) {
    PageTitle(R.string.lang_title, R.string.lang_lead)
    com.projectxo.app.core.model.AppLanguage.entries.forEach { l ->
        com.projectxo.app.core.ui.components.XoOption(languageName(l), null, l == s.language, { vm.setLanguage(l) })
    }
}

@Composable
private fun UnitsPage(vm: SettingsViewModel, s: AppSettings) {
    val p by vm.profile.collectAsStateWithLifecycle()
    PageTitle(R.string.units_title, R.string.units_lead)
    Label(stringResource(R.string.onb_weight), weightLabel(p.weightKg, s.units.weight))
    XoSegmented(
        listOf(WeightUnit.Kg to stringResource(R.string.unit_kg), WeightUnit.Lb to stringResource(R.string.unit_lb)),
        s.units.weight, { vm.setUnits(s.units.copy(weight = it)) },
    )
    Spacer(Modifier.height(XoSpacing.s5))
    Label(stringResource(R.string.onb_height), heightLabel(p.heightCm, s.units.height))
    XoSegmented(
        listOf(HeightUnit.Cm to stringResource(R.string.unit_cm), HeightUnit.FtIn to stringResource(R.string.unit_ft)),
        s.units.height, { vm.setUnits(s.units.copy(height = it)) },
    )
    Spacer(Modifier.height(XoSpacing.s5))
    val goalMl by vm.waterGoalMl.collectAsStateWithLifecycle()
    Label(
        stringResource(R.string.plan_water),
        "${volumeNumber(rememberFormatLocale(), goalMl, s.units.volume)} ${volumeUnitLabel(s.units.volume)}",
    )
    XoSegmented(
        listOf(VolumeUnit.Ml to stringResource(R.string.unit_water_ml), VolumeUnit.Oz to stringResource(R.string.unit_water_oz)),
        s.units.volume, { vm.setUnits(s.units.copy(volume = it)) },
    )
}

// ——— ключ для еды по фото (Р69) ———

@Composable
private fun FoodAiPage(vm: SettingsViewModel, s: AppSettings) {
    var keyError by remember { mutableStateOf(false) }
    PageTitle(R.string.set_foodai, if (BuildConfig.FOOD_AI_PROXY_URL.isNotBlank()) R.string.fp_key_optional else null)
    com.projectxo.app.feature.food.FoodAiKeyPanel(
        keyError = keyError,
        onSave = { keyError = !vm.setFoodAiKey(it) },
        onRemove = if (s.foodAiKey.isNotEmpty()) ({ vm.removeFoodAiKey() }) else null,
    )
}

// ——— уведомления и разрешения ———

@Composable
private fun NotificationsPage(vm: SettingsViewModel, s: AppSettings, onExample: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val context = LocalContext.current
    var notifOn by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var activityOn by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    // Вернулись из системных настроек: показываем актуальное состояние.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        activityOn = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val r = s.reminders
    // Android 13+: разрешение на уведомления спрашивается при включении напоминания.
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> notifOn = granted || NotificationManagerCompat.from(context).areNotificationsEnabled() }
    fun askIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !notifOn) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    PageTitle(R.string.notif_title, R.string.notif_lead)

    GroupTitle(stringResource(R.string.notif_perm_group))
    SettingsRow(
        XoIcons.Bell, stringResource(R.string.notif_perm_notifications),
        stringResource(if (notifOn) R.string.notif_perm_on else R.string.notif_perm_off),
    ) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    SettingsRow(
        XoIcons.Dumbbell, stringResource(R.string.notif_perm_activity),
        stringResource(if (activityOn) R.string.notif_activity_on else R.string.notif_activity_off),
    ) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s2))
    Text(stringResource(R.string.notif_open_system), style = type.caption, color = colors.muted)

    GroupTitle(stringResource(R.string.notif_reminders_group))
    XoHairline()
    if (Module.Water in s.modules) {
        XoSwitchRow(stringResource(R.string.rem_water), stringResource(R.string.rem_water_sub), r.water, { vm.setReminders(r.copy(water = it)); if (it) askIfNeeded() })
        XoHairline()
    }
    XoSwitchRow(stringResource(R.string.rem_summary), stringResource(R.string.rem_summary_sub), r.dailySummary, { vm.setReminders(r.copy(dailySummary = it)); if (it) askIfNeeded() })
    XoHairline()
    XoSwitchRow(stringResource(R.string.rem_weekly), stringResource(R.string.rem_weekly_sub), r.weeklySummary, { vm.setReminders(r.copy(weeklySummary = it)); if (it) askIfNeeded() })
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s4))
    val less = stringResource(R.string.less)
    val more = stringResource(R.string.more)
    Label(stringResource(R.string.notif_quiet_from))
    val fromText = clock(r.quietFromMin)
    XoStepper(fromText, "", stringResource(R.string.stepper_a11y, stringResource(R.string.notif_quiet_from), fromText, ""), less, more, { vm.stepQuiet(true, -1) }, { vm.stepQuiet(true, 1) })
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s4))
    Label(stringResource(R.string.notif_quiet_to))
    val toText = clock(r.quietToMin)
    XoStepper(toText, "", stringResource(R.string.stepper_a11y, stringResource(R.string.notif_quiet_to), toText, ""), less, more, { vm.stepQuiet(false, -1) }, { vm.stepQuiet(false, 1) })
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s3))
    Text(stringResource(R.string.notif_note), style = type.caption, color = colors.muted)
    Spacer(Modifier.height(XoSpacing.s3))
    SettingsRow(XoIcons.Bell, stringResource(R.string.notif_example), stringResource(if (notifOn) R.string.notif_example_sub else R.string.notif_example_blocked)) { onExample() }
    XoHairline()
}
