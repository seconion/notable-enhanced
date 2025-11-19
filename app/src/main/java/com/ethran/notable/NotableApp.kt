package com.ethran.notable

import android.app.Application
import com.onyx.android.sdk.rx.RxManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

class NotableApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            RxManager.Builder.initAppContext(this)
        } catch (e: Throwable) {
            android.util.Log.e("NotableApp", "Failed to initialize Onyx RxManager", e)
        }
        checkHiddenApiBypass()
    }

    private fun checkHiddenApiBypass() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }

}