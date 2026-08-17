package top.yzljc.sakuraba_ema.guild.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Iterator;
import java.util.Map;

public record ChannelCliResult(boolean success, int exitCode, JsonNode response, String stdout, String stderr,
                               boolean timedOut, int attempts) {

    public ChannelCliResult(boolean success, int exitCode, JsonNode response, String stdout,
                            String stderr, boolean timedOut, int attempts) {
        this.success = success;
        this.exitCode = exitCode;
        this.response = response == null ? MissingNode.getInstance() : response;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.timedOut = timedOut;
        this.attempts = attempts;
    }

    public ChannelCliResult withAttempts(int value) {
        return new ChannelCliResult(success, exitCode, response, stdout, stderr, timedOut, value);
    }

    public JsonNode getData() {
        return response.path("data");
    }

    public JsonNode getError() {
        JsonNode error = response.path("error");
        if (!error.isMissingNode()) {
            return error;
        }
        return stderr.isBlank() ? MissingNode.getInstance() : TextNode.valueOf(stderr);
    }

    public boolean isRateLimited() {
        return containsCode(response, 153)
                || containsIgnoreCase(stdout, "retCode\":153")
                || containsIgnoreCase(stderr, "retCode\":153")
                || containsIgnoreCase(stdout, "超过申请的频率上限")
                || containsIgnoreCase(stderr, "超过申请的频率上限");
    }

    public boolean isAuthenticationExpired() {
        return containsCode(response, 8011)
                || containsIgnoreCase(stdout, "retCode\":8011")
                || containsIgnoreCase(stderr, "retCode\":8011")
                || containsIgnoreCase(stdout, "未登录")
                || containsIgnoreCase(stderr, "未登录");
    }

    private static boolean containsIgnoreCase(String value, String expected) {
        return value != null && value.toLowerCase().contains(expected.toLowerCase());
    }

    private static boolean containsCode(JsonNode node, int expected) {
        if (node == null || node.isMissingNode()) {
            return false;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String name = field.getKey().replace("_", "").toLowerCase();
                JsonNode value = field.getValue();
                if ((name.equals("retcode") || name.equals("errorcode"))
                        && ((value.isIntegralNumber() && value.asInt() == expected)
                        || (value.isTextual() && value.asText().equals(String.valueOf(expected))))) {
                    return true;
                }
                if (containsCode(value, expected)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsCode(child, expected)) {
                    return true;
                }
            }
        }
        return false;
    }
}