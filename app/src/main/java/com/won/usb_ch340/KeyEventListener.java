package com.won.usb_ch340;

/**
 * 键盘上传回调事件
 */
public interface KeyEventListener {

    /**
     * @param data 键盘上传回调
     */
    void onKeyEvent(byte[] data);
}
