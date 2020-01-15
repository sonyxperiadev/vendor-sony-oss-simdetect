/*
 * Copyright (c) 2019-2020 The LineageOS Project
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

/*
 * Rewritten in Kotlin by Pavel Dubrova <pashadubrova@gmail.com>
 */

package com.sony.simdetect

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.UEventObserver

import com.android.internal.R
import com.android.internal.telephony.uicc.UiccSlot

class SimDetectService : Service() {
    private var TAG = "SimDetectService"
    // From drivers/misc/sim_detect.c
    private var NOTHING_HAPPENED = "0"
    private var SIM_REMOVED = "1"
    private var SIM_INSERTED = "2"
    // From src/java/com/android/internal/telephony/uicc/UiccSlot.java
    private var EVENT_CARD_REMOVED = 13
    private var EVENT_CARD_ADDED = 14

    private val lock = Any()

    private val simDetectEventObserver = object : UEventObserver() {
        override fun onUEvent(event: UEvent) {
            synchronized(lock) {
                val switchState = event.get("SWITCH_STATE")
                if (SIM_REMOVED.equals(switchState)) {
                    promptForRestart(false)
                } else if (SIM_INSERTED.equals(switchState)) {
                    promptForRestart(true)
                }
            }
        }
    }

    override fun onCreate() {
        val isHotSwapSupported = getResources().getBoolean(R.bool.config_hotswapCapable)
        if (!isHotSwapSupported) {
            simDetectEventObserver.startObserving("SWITCH_NAME=sim_detect")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun promptForRestart(isAdded: Boolean) {
        Handler(Looper.getMainLooper()).post({
            val uiccSlot = UiccSlot(this@SimDetectService, false)
            uiccSlot.sendMessage(uiccSlot.obtainMessage(
                    if (isAdded) EVENT_CARD_ADDED else EVENT_CARD_REMOVED, null))
        })
    }
}
