package com.offblink.keyou;

/** 启动模式 singleInstance：独占一个任务栈，全局唯一实例。 */
public class SingleInstanceLabActivity extends LaunchModeLabActivity {
    @Override
    protected String modeTag() {
        return "Keyou/Mode/singleInstance";
    }
}
