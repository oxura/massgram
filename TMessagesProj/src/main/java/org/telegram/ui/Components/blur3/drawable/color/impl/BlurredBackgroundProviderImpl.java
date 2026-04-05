package org.telegram.ui.Components.blur3.drawable.color.impl;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;

public class BlurredBackgroundProviderImpl {
    public static BlurredBackgroundProvider mainTabs(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                final float alpha = resolveGlassAlpha(isDark);
                final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                final int colorTarget = resolveGlassTargetColor(r, isDark, Theme.key_glass_targetMainTabs, 0.15f);
                return solveSrcColor(colorBg, colorTarget, alpha);
            })
            .setStrokeColorTop(0x14000000, 0x1FFFFFFF)
            .setStrokeColorBottom(0x22000000, 0x14FFFFFF)
            .setShadowColor(0x18000000, 0x08000000)
            .setShadowLayer(dpf2(4f), 0, dpf2(1f))
            .setStrokeWidth(dpf2(0.4f), dpf2(0.4f))
            .build();
    }

    public static BlurredBackgroundProvider topPanel(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                final float alpha = resolveGlassAlpha(isDark);
                final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                final int colorTarget = resolveGlassTargetColor(r, isDark, Theme.key_glass_targetMainTopPanel, 0.10f);
                return solveSrcColor(colorBg, colorTarget, alpha);
            })
            .setStrokeColorTop(0x12000000, 0x26FFFFFF)
            .setStrokeColorBottom(0x18000000, 0x14FFFFFF)
            .setShadowColor(0x18000000, 0x06000000)
            .setShadowLayer(dpf2(4f), 0, dpf2(1f))
            .setStrokeWidth(dpf2(0.4f), dpf2(0.4f))
            .build();
    }

    public static BlurredBackgroundProvider scrimMenuBackground(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) ->
                Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), isDark ? 0.85f : 0.76f))
            .setStrokeColorTop(0xFFFFFFFF, 0x20FFFFFF)
            .setStrokeColorBottom(0xFFFFFFFF, 0x20FFFFFF)
            .setShadowColor(0x26000000, 0)
            .setShadowLayer(dpf2(4f), 0, 0)
            .setStrokeWidth(dpf2(2 / 3f), dpf2(2 / 3f))
            .build();
    }

    public static BlurredBackgroundProvider attachMenuSearch(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    return Theme.multAlpha(colorBg, alpha);
                })
                .setStrokeColorTop(0x17000000, 0x17FFFFFF)
                .setStrokeColorBottom(0x17000000, 0x17FFFFFF)
                .setShadowColor(0x11000000, 0x04FFFFFF)
                .setShadowLayer(dpf2(2), 0, dpf2(1 / 3f))
                .setStrokeWidth(dpf2(0.4f), dpf2(0.4f))
                .build();
    }

    public static BlurredBackgroundProvider searchFloatingDate(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> 0x33000000)
                .setStrokeColorTop(0x17000000, 0x17FFFFFF)
                .setStrokeColorBottom(0x17000000, 0x17FFFFFF)
                .setShadowColor(0, 0)
                .setStrokeWidth(1, 1)
                .build();
    }

    public static BlurredBackgroundProvider topPanelChatActivity(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    if (!checkBlurEnabled(resourcesProvider)) {
                        return ColorUtils.setAlphaComponent(Theme.getColor(isDark ?
                            Theme.key_actionBarDefault : Theme.key_chat_topPanelBackground, r), 255);
                    }

                    final float alpha = resolveGlassAlpha(isDark);
                    final int colorBg = Theme.getColor(Theme.key_chat_topPanelBackground, r);
                    final int colorTarget = resolveGlassTargetColor(r, isDark, Theme.key_chat_topPanelBackground, 0.08f);
                    return solveSrcColor(colorBg, colorTarget, alpha);
                })
                .setStrokeColorTop(0xCCFFFFFF, 0x30FFFFFF)
                .setStrokeColorBottom(0x66FFFFFF, 0x12FFFFFF)
                .setShadowColor(0x18000000, 0)
                //.setShadowLayer(dpf2(10 / 3f), 0, dpf2(2 / 3f))
                .setStrokeWidth(dpf2(0.5f), dpf2(0.5f))
                .build();
    }

    public static BlurredBackgroundProvider inputFieldDialogActivity(Theme.ResourcesProvider resourcesProvider) {
        return topPanel(resourcesProvider);
    }

    public static BlurredBackgroundProvider inputFieldShareAlert(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    final int colorTarget = Theme.getColor(Theme.key_chat_messagePanelBackground, r);
                    return solveSrcColor(colorBg, colorTarget, alpha);
                })
                .setStrokeColorTop(0x17000000, 0x17FFFFFF)
                .setStrokeColorBottom(0x17000000, 0x17FFFFFF)
                .setShadowColor(0x26000000, 0x04FFFFFF)
                .setShadowLayer(dpf2(10 / 3f), 0, dpf2(2 / 3f))
                .setStrokeWidth(dpf2(0.4f), dpf2(0.4f))
                .build();
    }

    public static BlurredBackgroundProvider photoViewer(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.85f : 0.76f;
                    final int colorBg = 0xFF000000;
                    final int colorTarget = 0xFF1A1A1A;
                    return 0; // solveSrcColor(colorBg, colorTarget, alpha);
                })
                .setStrokeColorTop(0x28FFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0x14FFFFFF, 0x14FFFFFF)
                .setStrokeWidth(dpf2(2 / 3f), dpf2(2 / 3f))
                .build();
    }

    public static BlurredBackgroundProvider photoViewerMenu(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> 0x40000000)
                .setStrokeColorTop(0x28FFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0x14FFFFFF, 0x14FFFFFF)
                .setStrokeWidth(dpf2(2 / 3f), dpf2(2 / 3f))
                .build();
    }

    public static BlurredBackgroundProvider premiumButton(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) ->
                Theme.multAlpha(Theme.getColor(Theme.key_dialogBackground, r), 0.78f))
            .setStrokeColorTop(0xFFFFFFFF, 0x20FFFFFF)
            .setStrokeColorBottom(0, 0x20FFFFFF)
            .setShadowColor(0x30000000, 0x04FFFFFF)
            .setShadowLayer(dpf2(12 / 3f), 0, dpf2(1 / 3f))
            .setStrokeWidth(dpf2(0.67f), dpf2(0.67f))
            .build();
    }

    public static int solveSrcColor(int bgColor, int outColor, float alpha) {
        alpha = MathUtils.clamp(alpha, 0, 1);

        // Edge cases
        if (alpha <= 0f) {
            return Color.argb(0, 0, 0, 0);
        }
        if (alpha >= 1f) {
            return Color.argb(255, Color.red(outColor), Color.green(outColor), Color.blue(outColor));
        }

        final int bgR = Color.red(bgColor);
        final int bgG = Color.green(bgColor);
        final int bgB = Color.blue(bgColor);

        final int outR = Color.red(outColor);
        final int outG = Color.green(outColor);
        final int outB = Color.blue(outColor);

        final float invA = 1f - alpha;

        final int srcR = MathUtils.clamp(Math.round((outR - bgR * invA) / alpha), 0, 255);
        final int srcG = MathUtils.clamp(Math.round((outG - bgG * invA) / alpha), 0, 255);
        final int srcB = MathUtils.clamp(Math.round((outB - bgB * invA) / alpha), 0, 255);

        final int a8 = MathUtils.clamp(Math.round(alpha * 255f), 0, 255);

        return Color.argb(a8, srcR, srcG, srcB);
    }

    private static float resolveGlassAlpha(boolean isDark) {
        if (LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)) {
            return isDark ? 0.64f : 0.72f;
        }
        return isDark ? 0.74f : 0.80f;
    }

    private static int resolveGlassTargetColor(Theme.ResourcesProvider resourcesProvider, boolean isDark, int fallbackKey, float accentMix) {
        final int colorTarget = Theme.getColor(fallbackKey, resourcesProvider);
        final int accent = Theme.getColor(Theme.key_telegram_color, resourcesProvider);
        return ColorUtils.blendARGB(colorTarget, accent, isDark ? Math.min(accentMix + 0.04f, 0.25f) : accentMix);
    }

    public static boolean checkBlurEnabled(Theme.ResourcesProvider resourcesProvider) {
        return checkBlurEnabled(UserConfig.selectedAccount, resourcesProvider);
    }

    public static boolean checkBlurEnabled(int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        final boolean isLight = !isDark;
        boolean blurEnabled = SharedConfig.chatBlurEnabled();
        if (blurEnabled && isLight) {
            if (MessagesController.getInstance(currentAccount).config.disableBlurInLightTheme.get()) {
                blurEnabled = false;
            }
        }
        if (blurEnabled && isDark) {
            if (MessagesController.getInstance(currentAccount).config.disableBlurInDarkTheme.get()) {
                blurEnabled = false;
            }
        }
        return blurEnabled;
    }
}
