package top.yzljc.atribot.service.textreview;

/**
 * @Author YZ_Ljc_
 * @ClassName TextReviewException
 * @Created_at 2026/09/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.textreview
 */
public class TextReviewException extends RuntimeException {
    public TextReviewException(String message) {
        super(message);
    }

    public TextReviewException(String message, Throwable cause) {
        super(message, cause);
    }
}
