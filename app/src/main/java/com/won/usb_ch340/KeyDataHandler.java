package com.won.usb_ch340;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 键盘上传数据的处理类
 */
public class KeyDataHandler {

    public List<KeyBean> readData(byte[] b) {
        List<KeyBean> keyBeen = new ArrayList<KeyBean>();
        while (true) {
            b = getEffectiveData(b, keyBeen);
            if (b == null || b.length == 0) {
                break;
            }
        }
        return keyBeen;
    }
    //02 00 06 16 a3 00 00 01 07 03 b6
    //06 02 00 04 02 1c 00 00 03 19

    private byte[] getEffectiveData(byte[] b, List<KeyBean> keyBeen) {
        if (b.length > 0) {
            if (b.length >= 11 && b[0] == 0x02 && b[1] == 0x00 && b[2] == 0x06) {
                byte[] bytes = Arrays.copyOfRange(b, 0, 11);
                if (isEffective(bytes, true)) {
                    if (b[7] == 0x01) {
                        keyBeen.add(new KeyBean(0, b[8], true, (byte) 0x00));
                    }
                    return Arrays.copyOfRange(b, 11, b.length);
                }

            } else if (b.length >= 10 && b[0] == 0x06 && b[1] == 0x02 && b[2] == 0x00 && b[3] == 0x04) {

                byte[] bytes = Arrays.copyOfRange(b, 0, 10);
                if (isEffective(bytes, false) && bytes[6] == 0x00 && bytes[7] == 0x00) {

                    keyBeen.add(new KeyBean(1, (byte) 0, true, bytes[4]));
                    return Arrays.copyOfRange(b, 10, b.length);
                }
            }
        }
        return null;
    }

    public boolean isEffective(byte[] b, boolean isKey) {
        int r = 0x00;
        for (int i = isKey ? 1 : 2; i < b.length - 1; i++) {
            r = r ^ StringUtils.toHexInt(b[i]);
        }
        return r == StringUtils.toHexInt(b[b.length - 1]);
    }

    //020031021C8f2d29010c2701012a01642901402701012a0164290dc0 005f503d79726b6f537f7b00 2701012a016429019727010103 B2

    public byte[] write(String text, byte pckNum) {
        String c = "020031021C8f2d29010c2701012a01642901402701012a0164290dc" +
                "0000000000000000000000000" + "2701012a01642901972701010300";

        byte[] command = StringUtils.toByteArray(c);
        command[3] = pckNum;
        int index = -1;
        if (text.contains(".")) {
            index = text.indexOf(".");
            text = text.replace(".", "");
        }
        for (int i = 0; i < text.length(); i++) {
            command[39 - text.length() + i] = getCode(text.charAt(i), index - 1 == i);
        }
        int r = 0x00;
        for (int i = 0; i < command.length - 1; i++) {
            r = r ^ StringUtils.toHexInt(command[i]);
        }
        command[command.length - 1] = StringUtils.toByte(r);
        return command;
    }

    private byte getCode(char c, boolean point) {
        if ('1' == c) {
            return getFinishCode((byte) 0x50, point);
        } else if ('2' == c) {
            return getFinishCode((byte) 0x3d, point);
        } else if ('3' == c) {
            return getFinishCode((byte) 0x79, point);
        } else if ('4' == c) {
            return getFinishCode((byte) 0x72, point);
        } else if ('5' == c) {
            return getFinishCode((byte) 0x6b, point);
        } else if ('6' == c) {
            return getFinishCode((byte) 0x6f, point);
        } else if ('7' == c) {
            return getFinishCode((byte) 0x53, point);
        } else if ('8' == c) {
            return getFinishCode((byte) 0x7f, point);
        } else if ('9' == c) {
            return getFinishCode((byte) 0x7b, point);
        } else if ('0' == c) {
            return getFinishCode((byte) 0x5F, point);
        } else
            return 0x00;
    }

    private byte getFinishCode(byte b, boolean point) {
        if (!point) {
            return b;
        } else {
            return StringUtils.toByte(b ^ 0x80);
        }
    }
}
