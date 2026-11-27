package com.selfdrive.os;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 到点提醒的广播接收：弹一条普通通知。 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TEXT = "text";

    @Override
    public void onReceive(Context ctx, Intent i) {
        Notify.show(ctx,
                i == null ? null : i.getStringExtra(EXTRA_TITLE),
                i == null ? null : i.getStringExtra(EXTRA_TEXT));
    }
}
