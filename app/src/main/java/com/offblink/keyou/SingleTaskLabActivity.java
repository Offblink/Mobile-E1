package com.offblink.keyou;

/** 启动模式 singleTask：任务栈内复用，清掉其上方页面。 */
public class SingleTaskLabActivity extends LaunchModeLabActivity {
    @Override
    protected String modeTag() {
        return "Keyou/Mode/singleTask";
    }
}
