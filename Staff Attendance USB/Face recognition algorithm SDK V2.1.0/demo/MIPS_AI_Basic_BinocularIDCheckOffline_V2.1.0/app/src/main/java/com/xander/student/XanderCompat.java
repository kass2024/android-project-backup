package com.xander.student;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;

final class XanderCompat {
    private XanderCompat() {}

    static int pendingMutable() {
        int flags = 134217728; // FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 31) {
            flags |= 33554432; // FLAG_MUTABLE
        }
        return flags;
    }

    static void register(Context ctx, BroadcastReceiver rx, IntentFilter filter, boolean exported) {
        if (Build.VERSION.SDK_INT >= 33) {
            int flag = exported ? 0x2 : 0x4; // RECEIVER_EXPORTED / NOT_EXPORTED
            ctx.registerReceiver(rx, filter, flag);
        } else {
            ctx.registerReceiver(rx, filter);
        }
    }
}
