package com.selfdrive.os;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 闹钟到点时被系统唤起：弹通知 + 全屏打开 App，并安排下一天。 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String type = intent.getStringExtra("type");
        if (type == null) type = "wake";
        if ("sleep".equals(type)) {
            Notify.show(ctx, "自律日记 · 该写日记了",
                    "到就寝时间了，今天的心情日记还没写。", 1002);
        } else {
            Notify.show(ctx, "自律日记 · 起床挑战",
                    "拍一张洗漱完的照片，今天才算开始。", 1001);
        }
        // 安排下一天的同一时刻，实现每日循环
        AlarmSched.rescheduleAll(ctx.getApplicationContext());
    }
}
