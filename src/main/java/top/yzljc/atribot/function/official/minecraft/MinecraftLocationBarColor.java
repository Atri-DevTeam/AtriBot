package top.yzljc.atribot.function.official.minecraft;

import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftLocationBarColor
 * @Created_at 2026/08/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.minecraft
 */
public class MinecraftLocationBarColor {

    public static int getPlayerRGBColor(UUID uuid) {
        int hashCode = uuid.hashCode();
        return setBrightness(color(255, hashCode), 0.9F);
    }

    // skid form Minecraft ARGB.java, developed by Mojang Studios, in order to calculate the color of the location bar in Minecraft
    public static int red(final int color) {
        return color >> 16 & 255;
    }

    public static int green(final int color) {
        return color >> 8 & 255;
    }

    public static int blue(final int color) {
        return color & 255;
    }

    public static int color(final int alpha, final int red, final int green, final int blue) {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255;
    }

    public static int color(final int alpha, final int rgb) {
        return alpha << 24 | rgb & 16777215;
    }

    public static int alpha(final int color) {
        return color >>> 24;
    }

    public static int setBrightness(final int color, final float brightness) {
        int red = red(color);
        int green = green(color);
        int blue = blue(color);
        int alpha = alpha(color);
        int rgbMax = Math.max(Math.max(red, green), blue);
        int rgbMin = Math.min(Math.min(red, green), blue);
        float rgbConstantRange = (float)(rgbMax - rgbMin);
        float saturation;
        if (rgbMax != 0) {
            saturation = rgbConstantRange / (float)rgbMax;
        } else {
            saturation = 0.0F;
        }

        float hue;
        if (saturation == 0.0F) {
            hue = 0.0F;
        } else {
            float constantRed = (float)(rgbMax - red) / rgbConstantRange;
            float constantGreen = (float)(rgbMax - green) / rgbConstantRange;
            float constantBlue = (float)(rgbMax - blue) / rgbConstantRange;
            if (red == rgbMax) {
                hue = constantBlue - constantGreen;
            } else if (green == rgbMax) {
                hue = 2.0F + constantRed - constantBlue;
            } else {
                hue = 4.0F + constantGreen - constantRed;
            }

            hue /= 6.0F;
            if (hue < 0.0F) {
                ++hue;
            }
        }

        if (saturation == 0.0F) {
            red = green = blue = Math.round(brightness * 255.0F);
            return color(alpha, red, green, blue);
        } else {
            float colorWheelSegment = (hue - (float)Math.floor((double)hue)) * 6.0F;
            float colorWheelOffset = colorWheelSegment - (float)Math.floor((double)colorWheelSegment);
            float primaryColor = brightness * (1.0F - saturation);
            float secondaryColor = brightness * (1.0F - saturation * colorWheelOffset);
            float tertiaryColor = brightness * (1.0F - saturation * (1.0F - colorWheelOffset));
            switch ((int)colorWheelSegment) {
                case 0:
                    red = Math.round(brightness * 255.0F);
                    green = Math.round(tertiaryColor * 255.0F);
                    blue = Math.round(primaryColor * 255.0F);
                    break;
                case 1:
                    red = Math.round(secondaryColor * 255.0F);
                    green = Math.round(brightness * 255.0F);
                    blue = Math.round(primaryColor * 255.0F);
                    break;
                case 2:
                    red = Math.round(primaryColor * 255.0F);
                    green = Math.round(brightness * 255.0F);
                    blue = Math.round(tertiaryColor * 255.0F);
                    break;
                case 3:
                    red = Math.round(primaryColor * 255.0F);
                    green = Math.round(secondaryColor * 255.0F);
                    blue = Math.round(brightness * 255.0F);
                    break;
                case 4:
                    red = Math.round(tertiaryColor * 255.0F);
                    green = Math.round(primaryColor * 255.0F);
                    blue = Math.round(brightness * 255.0F);
                    break;
                case 5:
                    red = Math.round(brightness * 255.0F);
                    green = Math.round(primaryColor * 255.0F);
                    blue = Math.round(secondaryColor * 255.0F);
            }

            return color(alpha, red, green, blue);
        }
    }
}