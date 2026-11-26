package unified.qol.feature.foodhud;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Signed numbers shown next to the tooltip icons, formatted for the game language. No Minecraft types. */
public final class FoodTooltipText {
    private FoodTooltipText() {
    }

    /** Hunger points, for example {@code +5}. */
    public static String hunger(int nutrition) {
        return (nutrition >= 0 ? "+" : "-") + Math.abs(nutrition);
    }

    /** Saturation with one decimal in the game language's separator, for example {@code +1.2} or {@code +1,2}. */
    public static String saturation(float saturation, String languageCode) {
        DecimalFormat format = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(locale(languageCode)));
        float rounded = Math.round(saturation * 10.0f) / 10.0f;
        return (rounded >= 0.0f ? "+" : "-") + format.format(Math.abs(rounded));
    }

    /** {@code pt_br} to {@code pt-BR}; unknown or missing codes fall back to English. */
    static Locale locale(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return Locale.ROOT;
        }
        return Locale.forLanguageTag(languageCode.replace('_', '-'));
    }
}
