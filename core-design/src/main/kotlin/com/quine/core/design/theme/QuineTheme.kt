package com.quine.core.design.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.quine.core.design.token.DarkQuineColors
import com.quine.core.design.token.DefaultQuineDimens
import com.quine.core.design.token.DefaultQuineTypography
import com.quine.core.design.token.LightQuineColors
import com.quine.core.design.token.LocalQuineColors
import com.quine.core.design.token.LocalQuineDimens
import com.quine.core.design.token.LocalQuineMotion
import com.quine.core.design.token.LocalQuineTypography
import com.quine.core.design.token.LocalReducedMotion
import com.quine.core.design.token.QuineColors
import com.quine.core.design.token.QuineDimens
import com.quine.core.design.token.QuineMotion
import com.quine.core.design.token.QuineTypography

/**
 * 主题装配（docs/visual-spec.md §1、§6）。
 *
 * 双主题 = 镜像逻辑，跟随系统；两套都是亲儿子。`ink` 是唯一翻转的 token。
 * 同时把 token 映射进 Material3 `ColorScheme` / `Typography`，让 Material3 组件
 * （TextField、Switch 等）自动继承同一套语言。
 */
@Composable
fun QuineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkQuineColors else LightQuineColors
    val typography = DefaultQuineTypography
    val reducedMotion = rememberReducedMotion()
    val materialColors = remember(colors) { colors.toMaterialColorScheme() }
    val materialTypography = remember(typography) { typography.toMaterialTypography() }

    CompositionLocalProvider(
        LocalQuineColors provides colors,
        LocalQuineTypography provides typography,
        LocalQuineDimens provides DefaultQuineDimens,
        LocalQuineMotion provides QuineMotion.Default,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = materialTypography,
            content = content,
        )
    }
}

/** token 取用入口：`QuineTheme.colors.ink`、`QuineTheme.motion.standard`。 */
object QuineTheme {
    val colors: QuineColors
        @Composable @ReadOnlyComposable get() = LocalQuineColors.current

    val typography: QuineTypography
        @Composable @ReadOnlyComposable get() = LocalQuineTypography.current

    val dimens: QuineDimens
        @Composable @ReadOnlyComposable get() = LocalQuineDimens.current

    val motion: QuineMotion
        @Composable @ReadOnlyComposable get() = LocalQuineMotion.current

    /** 系统「移除动画」是否开启；动画必须据此降级。 */
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

/**
 * 系统「移除动画」（开发者选项 / 无障碍设置）检测。
 * 读取 `ANIMATOR_DURATION_SCALE`，为 0 表示动画被关闭。
 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

private fun QuineColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = ink,
        onPrimaryContainer = onInk,
        secondary = textSecondary,
        onSecondary = onInk,
        secondaryContainer = fill,
        onSecondaryContainer = textPrimary,
        tertiary = accent,
        onTertiary = onInk,
        background = bg,
        onBackground = textPrimary,
        surface = bg,
        onSurface = textPrimary,
        surfaceVariant = fill,
        onSurfaceVariant = textSecondary,
        surfaceContainer = fill,
        surfaceContainerHigh = fill,
        surfaceContainerHighest = fill,
        surfaceContainerLow = bg,
        surfaceContainerLowest = bg,
        outline = line,
        outlineVariant = line,
        error = danger,
        onError = onInk,
        errorContainer = fill,
        onErrorContainer = danger,
        scrim = scrim,
        inverseSurface = ink,
        inverseOnSurface = onInk,
    )
}

private fun QuineTypography.toMaterialTypography(): Typography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = display,
    headlineLarge = title,
    headlineMedium = title,
    headlineSmall = headline,
    titleLarge = title,
    titleMedium = headline,
    titleSmall = headline,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = headline,
    labelMedium = caption,
    labelSmall = caption,
)
