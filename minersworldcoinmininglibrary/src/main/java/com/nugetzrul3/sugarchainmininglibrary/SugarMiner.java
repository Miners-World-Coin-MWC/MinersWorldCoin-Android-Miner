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
        // YESPOWER,
        // YESPOWERSUGAR,
        YESPOWERMWC,
        YESPOWERADVC,
        // YESPOWERLITB,
        // YESPOWERIOTS,
        // YESPOWERMBC,
        // YESPOWERITC,
        // YESPOWERISO,
    }

    private static Handler sHandler;

    public SugarMiner(Handler handler) {
        sHandler = handler;
    }
    public int beginMiner(String pool, String username, String pwd, int threads, Algorithms algorithm) {
        switch (algorithm) {
            // case YESPOWER:
            //     return startMining(pool, username, pwd, threads, 0);
            case YESPOWERMWC:
                return startMining(pool, username, pwd, threads, 2);
            case YESPOWERADVC:
                return startMining(pool, username, pwd, threads, 3);
            /*case YESPOWERSUGAR:
                return startMining(pool, username, pwd, threads, 1);
            case YESPOWERLITB:
                return startMining(pool, username, pwd, threads, 3);
            case YESPOWERIOTS:
                return startMining(pool, username, pwd, threads, 4);
            case YESPOWERMBC:
                return startMining(pool, username, pwd, threads, 5);
            case YESPOWERITC:
                return startMining(pool, username, pwd, threads, 6);
            case YESPOWERISO:
                return startMining(pool, username, pwd, threads, 7);*/
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
