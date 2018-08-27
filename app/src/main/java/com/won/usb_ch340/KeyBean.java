package com.won.usb_ch340;

public class KeyBean {

    //事件类型：0：键盘上报，1：透传响应
    private int type;

    //如果type==0，keyCode表示按键值
    private byte keyCode;

    //如果type==1,success表示是否显示成功
    private boolean success;

    private byte pckNum;

    public KeyBean(int type, byte keyCode, boolean success, byte pckNum) {
        this.type = type;
        this.keyCode = keyCode;
        this.success = success;
        this.pckNum = pckNum;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public byte getKeyCode() {
        return keyCode;
    }

    public void setKeyCode(byte keyCode) {
        this.keyCode = keyCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public byte getPckNum() {
        return pckNum;
    }

    public void setPckNum(byte pckNum) {
        this.pckNum = pckNum;
    }
}
