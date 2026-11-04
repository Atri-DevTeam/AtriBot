package top.yzljc.atribot.survey;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName SurveyId
 * @Created_at 2026/10/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.survey
 */
public final class SurveyId {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{12}");

    private SurveyId() {}

    public static String create() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }
}
