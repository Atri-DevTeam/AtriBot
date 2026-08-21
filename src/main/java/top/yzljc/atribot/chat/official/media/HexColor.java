package top.yzljc.atribot.chat.official.media;

public record HexColor(String value) {
    public HexColor {
        if (!value.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("Invalid color: " + value);
        }
    }
}