package top.yzljc.qqbot.config.webui.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.yzljc.qqbot.config.Result;
import top.yzljc.qqbot.config.webui.WebUIController;

/**
 * @Author YZ_Ljc_
 * @ClassName ExceptionController
 * @Created_at 2026/05/02
 * @Project AtriBot
 * @Package top.yzljc.qqbot.config.webui.exception
 */
@RestControllerAdvice
public class ExceptionController {

    @ExceptionHandler
    public Result<WebUIController.ToggleFeatureDTO> handleFeatureException(FeatureNotFoundException e) {
        return Result.fail(404, e.getMessage());
    }
}