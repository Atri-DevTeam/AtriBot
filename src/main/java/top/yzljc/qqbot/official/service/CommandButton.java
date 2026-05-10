package top.yzljc.qqbot.official.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommandButton {
        private String id;
        private String label;
        private String command;
        private boolean enter;
        private int style;
        private int type;
}