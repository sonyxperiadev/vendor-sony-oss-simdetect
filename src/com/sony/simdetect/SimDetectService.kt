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

import android.app.AlertDialog
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.UEventObserver
import android.util.Log
import android.view.WindowManager

import com.android.internal.R
import com.android.internal.telephony.uicc.UiccCard.EXTRA_ICC_CARD_ADDED

class SimDetectService : Service() {
    private var TAG = "SimDetectService"
    // From drivers/misc/sim_detect.c
    private var NOTHING_HAPPENED = "0"
    private var SIM_REMOVED = "1"
    private var SIM_INSERTED = "2"

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
        synchronized (lock) {
            val res = getResources()
            val dialogComponent = res.getString(
                    R.string.config_iccHotswapPromptForRestartDialogComponent)

            if (dialogComponent != null) {
                val intent = Intent()
                        .setComponent(ComponentName.unflattenFromString(dialogComponent))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EXTRA_ICC_CARD_ADDED, isAdded)

                try {
                    startActivity(intent)
                    return
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "Unable to find ICC hotswap prompt for restart activity: " + e);
                }

                // TODO: Here we assume the device can't handle SIM hot-swap
                //      and has to reboot. We may want to add a property,
                //      e.g. REBOOT_ON_SIM_SWAP, to indicate if modem support
                //      hot-swap.
                val listener = DialogInterface.OnClickListener { _, which ->
                    synchronized (lock) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            val pm = getSystemService(PowerManager::class.java)
                            pm.reboot("SIM is added.")
                        }
                    }
                }

                val r = Resources.getSystem()
                val title = r.getString(
                        if (isAdded) R.string.sim_added_title else R.string.sim_removed_title)
                val message = r.getString(
                        if (isAdded) R.string.sim_added_message else R.string.sim_removed_message)
                val buttonText = r.getString(R.string.sim_restart_button)

                Handler(Looper.getMainLooper()).post({
                    val dialog = AlertDialog.Builder(this)
                            .setTitle(title)
                            .setMessage(message)
                            .setPositiveButton(buttonText, listener)
                            .create()
                    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                    dialog.show()
                })
            }
        }
    }
}
