package com.selfdrive.os;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机完成：按上次保存的时间重排打卡/就寝提醒。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent i) {
        if (i != null && Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
            NativeUI.rescheduleFromPrefs(ctx.getApplicationContext());
        }
    }
}
