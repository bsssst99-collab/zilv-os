package com.selfdrive.os;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机 / 覆盖安装后，按保存的设置重建闹钟。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        AlarmSched.rescheduleAll(ctx.getApplicationContext());
    }
}
