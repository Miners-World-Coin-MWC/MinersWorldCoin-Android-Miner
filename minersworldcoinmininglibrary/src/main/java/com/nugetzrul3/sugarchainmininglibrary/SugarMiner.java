package com.nugetzrul3.minersworldcoinmininglibrary;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class SugarMiner {
    private static final String TAG = "SugarMiner";

    static {
        System.loadLibrary("sugarminer");
    }

    public enum Algorithms {
        // ALGO_SUGAR_YESPOWER_1_0_1,
        // ALGO_ISO_YESPOWER_1_0_1,
        // ALGO_URX_YESPOWER_1_0_1,
        // ALGO_LITB_YESPOWER_1_0_1,
        // ALGO_IOTS_YESPOWER_1_0_1,
        // ALGO_ITC_YESPOWER_1_0_1,
        ALGO_MBC_YESPOWER_1_0_1,
        // ALGO_YTN_YESPOWER_1_0_1,
        ALGO_ADVC_YESPOWER_1_0_1,
        ALGO_MWC_YESPOWER_1_0_1,
    }

    private static Handler sHandler;

    public SugarMiner(Handler handler) {
        sHandler = handler;
    }
    public int beginMiner(String pool, String username, String pwd, int threads, Algorithms algorithm) {
        switch (algorithm) {

            case ALGO_MWC_YESPOWER_1_0_1:
                return startMining(pool, username, pwd, threads, 2);
            case ALGO_ADVC_YESPOWER_1_0_1:
                return startMining(pool, username, pwd, threads, 1);
            
            default:
                return -1;
        }
    }

    private static void output(String message) {
        Message msg = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("log", message);
        msg.setData(bundle);

        if (sHandler != null) {
            sHandler.sendMessage(msg);
        }
    }

    public native int getAcceptedCount();
    public native int getRejectedCount();

    public native int stopMining();
    private native int startMining(String url, String user, String password, int n_threads, int algo);
    public native int initMining();
}
