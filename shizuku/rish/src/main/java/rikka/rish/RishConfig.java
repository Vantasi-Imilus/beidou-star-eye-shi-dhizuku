package rikka.rish;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.util.Log;

public class RishConfig {

    private static final String TAG = "RISHConfig";

    static final int TRANSACTION_createHost = 0;
    static final int TRANSACTION_setWindowSize = 1;
    static final int TRANSACTION_getExitCode = 2;

    private static IBinder binder;
    private static String interfaceToken;
    private static int transactionCodeStart;
    private static String libraryPath;

    static IBinder getBinder() {
        return binder;
    }

    static String getInterfaceToken() {
        return interfaceToken;
    }

    static int getTransactionCode(int code) {
        return transactionCodeStart + code;
    }

    public static void setLibraryPath(String path) {
        libraryPath = path;
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private static void loadLibrary() {
        try {
            if (libraryPath == null) {
                System.loadLibrary("rish");
            } else {
                System.load(libraryPath + "/librish.so");
            }
        } catch (Throwable t) {
            // 原生 librish.so 未打包时优雅降级，不影响授权等核心能力
            Log.w(TAG, "rish native library not available: " + t);
        }
    }

    public static void init(String interfaceToken, int transactionCodeStart) {
        Log.d(TAG, "init (server) " + interfaceToken + " " + transactionCodeStart);
        RishConfig.interfaceToken = interfaceToken;
        RishConfig.transactionCodeStart = transactionCodeStart;
        loadLibrary();
    }

    public static void init(IBinder binder, String interfaceToken, int transactionCodeStart) {
        Log.d(TAG, "init (client) " + binder + " " + interfaceToken + " " + transactionCodeStart);
        RishConfig.binder = binder;
        RishConfig.interfaceToken = interfaceToken;
        RishConfig.transactionCodeStart = transactionCodeStart;
        loadLibrary();
    }
}
