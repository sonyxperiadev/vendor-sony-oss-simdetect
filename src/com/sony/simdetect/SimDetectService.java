/*
 * Copyright (c) 2019 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sony.simdetect;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.UEventObserver;
import com.android.internal.R;
import com.android.internal.telephony.uicc.UiccSlot;

public class SimDetectService extends Service {
    private static final String TAG = "SimDetectService";

    // From drivers/misc/sim_detect.c
    private static final String NOTHING_HAPPENED = "0";
    private static final String SIM_REMOVED = "1";
    private static final String SIM_INSERTED = "2";

    // From src/java/com/android/internal/telephony/uicc/UiccSlot.java
    private static final int EVENT_CARD_REMOVED = 13;
    private static final int EVENT_CARD_ADDED = 14;

    private final Object mLock = new Object();

    private final UEventObserver mSimDetectEventObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEvent event) {
            synchronized (mLock) {
                String switchState = event.get("SWITCH_STATE");

                if (SIM_REMOVED.equals(switchState)) {
                    promptForRestart(false);
                } else if (SIM_INSERTED.equals(switchState)) {
                    promptForRestart(true);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        boolean isHotSwapSupported = getResources().getBoolean(R.bool.config_hotswapCapable);

        if (!isHotSwapSupported) {
            mSimDetectEventObserver.startObserving("SWITCH_NAME=sim_detect");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void promptForRestart(boolean isAdded) {
        new Handler(Looper.getMainLooper()).post(() -> {
            UiccSlot uiccSlot = new UiccSlot(SimDetectService.this, false);
            uiccSlot.sendMessage(uiccSlot.obtainMessage(
                    isAdded ? EVENT_CARD_ADDED : EVENT_CARD_REMOVED, null));
        });
    }
}
