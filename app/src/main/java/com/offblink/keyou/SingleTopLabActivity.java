package com.offblink.keyou;

/** 启动模式 singleTop：栈顶复用，走 onNewIntent；不在栈顶则新建。 */
public class SingleTopLabActivity extends LaunchModeLabActivity {
    @Override
    protected String modeTag() {
        return "Keyou/Mode/singleTop";
    }
}
