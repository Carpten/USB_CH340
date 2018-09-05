package com.won.usb_ch340;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity implements KeyEventListener {

    private CH340AndroidDriver ch340AndroidDriver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ch340AndroidDriver = new CH340AndroidDriver(this);
        ch340AndroidDriver.setKeyEventListener(this);
        ch340AndroidDriver.start();
        new SyncThread().start();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWriteEnable = false;
        ch340AndroidDriver.close();
    }


    @Override
    public void onKeyEvent(byte[] data) {
        List<KeyBean> keyBeans = new KeyDataHandler().readData(data);
        for (KeyBean keyBean : keyBeans) {
            if (keyBean.getType() == 1) {// 首先处理回显响应事件
                Log.i("Test", StringUtils.toHexString(data, data.length));
                if (keyBean.isSuccess() && keyBean.getPckNum() == mPckNum) {
                    mKeyInit = true;
                    mAmount = mTempAmount;
                }
            }
        }
        for (KeyBean keyBean : keyBeans) {
            if (keyBean.getType() == 0 && mWriteEnable) {// 再处理按键事件
                Log.i("Test", StringUtils.toHexString(data, data.length));
                handlerKeyEvent(keyBean.getKeyCode());
//                getText(bindService())
//                appendString.append(getText(keyBean.getKeyCode()));
            }
        }
    }

    String mAmount = "0";//键盘上显示的金额，也是实际金额

    String mTempAmount = "0";//临时金额，待同步到mAmount的最新金额

    boolean mKeyInit = false;//初始化

    boolean mWriteEnable = true;//用来控制是否写入键盘

    byte mPckNum = 0x00;//包序号

    private void addAmount(String appendAmount) {
        int index = mTempAmount.lastIndexOf(".");
        if (index > -1 && ".".equals(appendAmount)) {
            return;
        }

        if (index > -1 && index < mTempAmount.length() - 2) {
            return;
        }

        String afterAmount = mTempAmount + appendAmount;
        while (afterAmount.startsWith("00")) {
            afterAmount = afterAmount.substring(1, afterAmount.length());
        }
        while (afterAmount.startsWith("0") && afterAmount.lastIndexOf(".") != 1 && afterAmount.length() > 1) {
            afterAmount = afterAmount.substring(1, afterAmount.length());
        }
        if (Double.valueOf(afterAmount) <= 99999.99) {
//            int i = ch340AndroidDriver.WriteData(new KeyDataHandler().write(afterAmount));
//            if (i == 54) {
            addPckNum();
            mTempAmount = afterAmount;
            ch340AndroidDriver.WriteData(new KeyDataHandler().write(afterAmount, getPckNum()));
//            }
        }
    }


    private void deleteAmount() {
        if (mTempAmount.length() <= 1) {
            mTempAmount = "0";
        } else {
            mTempAmount = mTempAmount.substring(0, mTempAmount.length() - 1);
        }
        addPckNum();
        int i = ch340AndroidDriver.WriteData(new KeyDataHandler().write(mTempAmount, getPckNum()));
    }


    private void handlerKeyEvent(byte b) {
        if (b == 0x17) {//enter键
            mWriteEnable = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    if (mAmount.equals(mTempAmount)) {//已正常同步
                        Toast.makeText(MainActivity.this, "确认键", Toast.LENGTH_SHORT).show();
                    } else {//未收到最新键盘同步事件，等待300ms后处理
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "确认键", Toast.LENGTH_SHORT).show();
                            }
                        }, 300);
                    }
                }
            });
        } else if (b == 0x1e) {//返回键
            deleteAmount();
        } else if (b == 0x1d) {//回退键

        } else if (b == 0x1f) {
            addAmount("0");
        } else if (b == 0x02) {
            addAmount("1");
        } else if (b == 0x01) {
            addAmount("2");
        } else if (b == 0x10) {
            addAmount("3");
        } else if (b == 0x05) {
            addAmount("4");
        } else if (b == 0x04) {
            addAmount("5");
        } else if (b == 0x03) {
            addAmount("6");
        } else if (b == 0x08) {
            addAmount("7");
        } else if (b == 0x07) {
            addAmount("8");
        } else if (b == 0x06) {
            addAmount("9");
        } else if (b == 0x0e) {
            addAmount(".");
        }
    }

    public synchronized byte getPckNum() {
        return mPckNum;
    }

    public void addPckNum() {
        this.mPckNum++;
    }


    /**
     * 　同步线程，当mTempAmout的值与mAmout的值不一致时，会同步mTempAmout到小键盘
     */
    class SyncThread extends Thread {


        @Override
        public void run() {

            while (mWriteEnable) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (!mAmount.equals(mTempAmount) || !mKeyInit) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (!mAmount.equals(mTempAmount) || !mKeyInit) {
                        ch340AndroidDriver.WriteData(new KeyDataHandler().write(mTempAmount, getPckNum()));
                    }
                }

            }
        }
    }


}
