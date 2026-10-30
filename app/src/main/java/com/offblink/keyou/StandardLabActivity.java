package com.offblink.keyou;

/** 启动模式 standard：每次启动都新建实例（默认行为）。 */
public class StandardLabActivity extends LaunchModeLabActivity {
    @Override
    protected String modeTag() {
        return "Keyou/Mode/standard";
    }
}
