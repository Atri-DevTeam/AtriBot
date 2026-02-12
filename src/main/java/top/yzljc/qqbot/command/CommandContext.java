package top.yzljc.qqbot.command;

public class CommandContext {
    private final String command;
    private final Long groupId;
    private final Long userId;
    private final String rawMsg;
    private final Boolean isAdmin;
    private final Integer messageId;
    private final Boolean isAtBot;
    private final Boolean isEnabled;
    private final Boolean isDebug;

    private CommandContext(Builder ct) {
        this.command = ct.command;
        this.groupId = ct.groupId;
        this.userId = ct.userId;
        this.rawMsg = ct.rawMsg;
        this.isAdmin = ct.isAdmin;
        this.messageId = ct.messageId;
        this.isAtBot = ct.isAtBot;
        this.isEnabled = ct.isEnabled;
        this.isDebug = ct.isDebug;
    }

    public String getCommand() {
        return command;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRawMsg() {
        return rawMsg;
    }

    public Boolean getIsAdmin() {
        return isAdmin;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public Boolean getIsAtBot() {
        return isAtBot;
    }

    public Boolean getIsEnabled() {
        return isEnabled;
    }

    public Boolean getIsDebug() {
        return isDebug;
    }

    public static Builder builder(String command) {
        return new Builder(command);
    }

    public static class Builder {
        private final String command;
        private Long groupId;
        private Long userId;
        private String rawMsg;
        private Boolean isAdmin;
        private Integer messageId;
        private Boolean isAtBot;
        private Boolean isEnabled;
        private Boolean isDebug;

        public Builder(String command) {
            this.command = command;
        }

        public Builder groupId(Long groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder rawMsg(String rawMsg) {
            this.rawMsg = rawMsg;
            return this;
        }

        public Builder isAdmin(Boolean isAdmin) {
            this.isAdmin = isAdmin;
            return this;
        }

        public Builder messageId(Integer messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder isAtBot(Boolean isAtBot) {
            this.isAtBot = isAtBot;
            return this;
        }

        public Builder isEnabled(Boolean isEnabled) {
            this.isEnabled = isEnabled;
            return this;
        }

        public Builder isDebug(Boolean isDebug) {
            this.isDebug = isDebug;
            return this;
        }

        public CommandContext build() {
            return new CommandContext(this);
        }
    }
}