package top.yzljc.atribot.chat.official.media;

/**
 * @Author YZ_Ljc_
 * @ClassName HexColor
 * @Created_at 2026/08/02
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official.media
 * @param value
 */
public record HexColor(String value) {
    // 基础颜色
    public static final HexColor BLACK = new HexColor("#000000");
    public static final HexColor WHITE = new HexColor("#FFFFFF");
    public static final HexColor RED = new HexColor("#FF0000");
    public static final HexColor LIME = new HexColor("#00FF00");
    public static final HexColor BLUE = new HexColor("#0000FF");
    public static final HexColor YELLOW = new HexColor("#FFFF00");
    public static final HexColor CYAN = new HexColor("#00FFFF");
    public static final HexColor MAGENTA = new HexColor("#FF00FF");
    public static final HexColor SILVER = new HexColor("#C0C0C0");
    public static final HexColor GRAY = new HexColor("#808080");
    public static final HexColor MAROON = new HexColor("#800000");
    public static final HexColor OLIVE = new HexColor("#808000");
    public static final HexColor GREEN = new HexColor("#008000");
    public static final HexColor PURPLE = new HexColor("#800080");
    public static final HexColor TEAL = new HexColor("#008080");
    public static final HexColor NAVY = new HexColor("#000080");
    public static final HexColor AQUA = CYAN;
    public static final HexColor FUCHSIA = MAGENTA;

    // 红色系
    public static final HexColor DARK_RED = new HexColor("#8B0000");
    public static final HexColor FIREBRICK = new HexColor("#B22222");
    public static final HexColor CRIMSON = new HexColor("#DC143C");
    public static final HexColor INDIAN_RED = new HexColor("#CD5C5C");
    public static final HexColor LIGHT_CORAL = new HexColor("#F08080");
    public static final HexColor SALMON = new HexColor("#FA8072");
    public static final HexColor DARK_SALMON = new HexColor("#E9967A");
    public static final HexColor LIGHT_SALMON = new HexColor("#FFA07A");

    // 粉色系
    public static final HexColor PINK = new HexColor("#FFC0CB");
    public static final HexColor LIGHT_PINK = new HexColor("#FFB6C1");
    public static final HexColor HOT_PINK = new HexColor("#FF69B4");
    public static final HexColor DEEP_PINK = new HexColor("#FF1493");
    public static final HexColor PALE_VIOLET_RED = new HexColor("#DB7093");
    public static final HexColor MEDIUM_VIOLET_RED = new HexColor("#C71585");

    // 橙色系
    public static final HexColor CORAL = new HexColor("#FF7F50");
    public static final HexColor TOMATO = new HexColor("#FF6347");
    public static final HexColor ORANGE_RED = new HexColor("#FF4500");
    public static final HexColor DARK_ORANGE = new HexColor("#FF8C00");
    public static final HexColor ORANGE = new HexColor("#FFA500");

    // 黄色系
    public static final HexColor GOLD = new HexColor("#FFD700");
    public static final HexColor LIGHT_YELLOW = new HexColor("#FFFFE0");
    public static final HexColor LEMON_CHIFFON = new HexColor("#FFFACD");
    public static final HexColor LIGHT_GOLDENROD_YELLOW = new HexColor("#FAFAD2");
    public static final HexColor PAPAYA_WHIP = new HexColor("#FFEFD5");
    public static final HexColor MOCCASIN = new HexColor("#FFE4B5");
    public static final HexColor PEACH_PUFF = new HexColor("#FFDAB9");
    public static final HexColor PALE_GOLDENROD = new HexColor("#EEE8AA");
    public static final HexColor KHAKI = new HexColor("#F0E68C");
    public static final HexColor DARK_KHAKI = new HexColor("#BDB76B");

    // 绿色系
    public static final HexColor GREEN_YELLOW = new HexColor("#ADFF2F");
    public static final HexColor CHARTREUSE = new HexColor("#7FFF00");
    public static final HexColor LAWN_GREEN = new HexColor("#7CFC00");
    public static final HexColor LIME_GREEN = new HexColor("#32CD32");
    public static final HexColor PALE_GREEN = new HexColor("#98FB98");
    public static final HexColor LIGHT_GREEN = new HexColor("#90EE90");
    public static final HexColor MEDIUM_SPRING_GREEN = new HexColor("#00FA9A");
    public static final HexColor SPRING_GREEN = new HexColor("#00FF7F");
    public static final HexColor MEDIUM_SEA_GREEN = new HexColor("#3CB371");
    public static final HexColor SEA_GREEN = new HexColor("#2E8B57");
    public static final HexColor FOREST_GREEN = new HexColor("#228B22");
    public static final HexColor DARK_GREEN = new HexColor("#006400");
    public static final HexColor YELLOW_GREEN = new HexColor("#9ACD32");
    public static final HexColor OLIVE_DRAB = new HexColor("#6B8E23");
    public static final HexColor DARK_OLIVE_GREEN = new HexColor("#556B2F");
    public static final HexColor MEDIUM_AQUAMARINE = new HexColor("#66CDAA");
    public static final HexColor DARK_SEA_GREEN = new HexColor("#8FBC8F");
    public static final HexColor LIGHT_SEA_GREEN = new HexColor("#20B2AA");
    public static final HexColor DARK_CYAN = new HexColor("#008B8B");

    // 青色系
    public static final HexColor AQUAMARINE = new HexColor("#7FFFD4");
    public static final HexColor TURQUOISE = new HexColor("#40E0D0");
    public static final HexColor MEDIUM_TURQUOISE = new HexColor("#48D1CC");
    public static final HexColor DARK_TURQUOISE = new HexColor("#00CED1");
    public static final HexColor PALE_TURQUOISE = new HexColor("#AFEEEE");
    public static final HexColor LIGHT_CYAN = new HexColor("#E0FFFF");

    // 蓝色系
    public static final HexColor POWDER_BLUE = new HexColor("#B0E0E6");
    public static final HexColor LIGHT_BLUE = new HexColor("#ADD8E6");
    public static final HexColor LIGHT_SKY_BLUE = new HexColor("#87CEFA");
    public static final HexColor SKY_BLUE = new HexColor("#87CEEB");
    public static final HexColor DEEP_SKY_BLUE = new HexColor("#00BFFF");
    public static final HexColor LIGHT_STEEL_BLUE = new HexColor("#B0C4DE");
    public static final HexColor DODGER_BLUE = new HexColor("#1E90FF");
    public static final HexColor CORNFLOWER_BLUE = new HexColor("#6495ED");
    public static final HexColor STEEL_BLUE = new HexColor("#4682B4");
    public static final HexColor CADET_BLUE = new HexColor("#5F9EA0");
    public static final HexColor MEDIUM_SLATE_BLUE = new HexColor("#7B68EE");
    public static final HexColor SLATE_BLUE = new HexColor("#6A5ACD");
    public static final HexColor DARK_SLATE_BLUE = new HexColor("#483D8B");
    public static final HexColor ROYAL_BLUE = new HexColor("#4169E1");
    public static final HexColor MEDIUM_BLUE = new HexColor("#0000CD");
    public static final HexColor DARK_BLUE = new HexColor("#00008B");
    public static final HexColor MIDNIGHT_BLUE = new HexColor("#191970");

    // 紫色系
    public static final HexColor LAVENDER = new HexColor("#E6E6FA");
    public static final HexColor THISTLE = new HexColor("#D8BFD8");
    public static final HexColor PLUM = new HexColor("#DDA0DD");
    public static final HexColor VIOLET = new HexColor("#EE82EE");
    public static final HexColor ORCHID = new HexColor("#DA70D6");
    public static final HexColor MEDIUM_ORCHID = new HexColor("#BA55D3");
    public static final HexColor DARK_ORCHID = new HexColor("#9932CC");
    public static final HexColor DARK_VIOLET = new HexColor("#9400D3");
    public static final HexColor BLUE_VIOLET = new HexColor("#8A2BE2");
    public static final HexColor DARK_MAGENTA = new HexColor("#8B008B");
    public static final HexColor MEDIUM_PURPLE = new HexColor("#9370DB");
    public static final HexColor REBECCA_PURPLE = new HexColor("#663399");
    public static final HexColor INDIGO = new HexColor("#4B0082");

    // 棕色与米色系
    public static final HexColor CORNSILK = new HexColor("#FFF8DC");
    public static final HexColor BLANCHED_ALMOND = new HexColor("#FFEBCD");
    public static final HexColor BISQUE = new HexColor("#FFE4C4");
    public static final HexColor NAVAJO_WHITE = new HexColor("#FFDEAD");
    public static final HexColor WHEAT = new HexColor("#F5DEB3");
    public static final HexColor BURLY_WOOD = new HexColor("#DEB887");
    public static final HexColor TAN = new HexColor("#D2B48C");
    public static final HexColor ROSY_BROWN = new HexColor("#BC8F8F");
    public static final HexColor SANDY_BROWN = new HexColor("#F4A460");
    public static final HexColor GOLDENROD = new HexColor("#DAA520");
    public static final HexColor DARK_GOLDENROD = new HexColor("#B8860B");
    public static final HexColor PERU = new HexColor("#CD853F");
    public static final HexColor CHOCOLATE = new HexColor("#D2691E");
    public static final HexColor SADDLE_BROWN = new HexColor("#8B4513");
    public static final HexColor SIENNA = new HexColor("#A0522D");
    public static final HexColor BROWN = new HexColor("#A52A2A");

    // 白色与浅色系
    public static final HexColor SNOW = new HexColor("#FFFAFA");
    public static final HexColor HONEYDEW = new HexColor("#F0FFF0");
    public static final HexColor MINT_CREAM = new HexColor("#F5FFFA");
    public static final HexColor AZURE = new HexColor("#F0FFFF");
    public static final HexColor ALICE_BLUE = new HexColor("#F0F8FF");
    public static final HexColor GHOST_WHITE = new HexColor("#F8F8FF");
    public static final HexColor WHITE_SMOKE = new HexColor("#F5F5F5");
    public static final HexColor SEASHELL = new HexColor("#FFF5EE");
    public static final HexColor BEIGE = new HexColor("#F5F5DC");
    public static final HexColor OLD_LACE = new HexColor("#FDF5E6");
    public static final HexColor FLORAL_WHITE = new HexColor("#FFFAF0");
    public static final HexColor IVORY = new HexColor("#FFFFF0");
    public static final HexColor ANTIQUE_WHITE = new HexColor("#FAEBD7");
    public static final HexColor LINEN = new HexColor("#FAF0E6");
    public static final HexColor LAVENDER_BLUSH = new HexColor("#FFF0F5");
    public static final HexColor MISTY_ROSE = new HexColor("#FFE4E1");

    // 灰色系及 GREY 拼写别名
    public static final HexColor GAINSBORO = new HexColor("#DCDCDC");
    public static final HexColor LIGHT_GRAY = new HexColor("#D3D3D3");
    public static final HexColor DARK_GRAY = new HexColor("#A9A9A9");
    public static final HexColor DIM_GRAY = new HexColor("#696969");
    public static final HexColor LIGHT_SLATE_GRAY = new HexColor("#778899");
    public static final HexColor SLATE_GRAY = new HexColor("#708090");
    public static final HexColor DARK_SLATE_GRAY = new HexColor("#2F4F4F");
    public static final HexColor GREY = GRAY;
    public static final HexColor LIGHT_GREY = LIGHT_GRAY;
    public static final HexColor DARK_GREY = DARK_GRAY;
    public static final HexColor DIM_GREY = DIM_GRAY;
    public static final HexColor LIGHT_SLATE_GREY = LIGHT_SLATE_GRAY;
    public static final HexColor SLATE_GREY = SLATE_GRAY;
    public static final HexColor DARK_SLATE_GREY = DARK_SLATE_GRAY;

    public HexColor {
        if (!value.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("Invalid color: " + value);
        }
    }
}