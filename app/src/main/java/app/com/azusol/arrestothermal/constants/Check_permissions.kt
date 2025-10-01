package app.com.azusol.arrestothermal.constants

import android.Manifest.permission
import android.R
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat

class Check_permissions {

    companion object {
        var CAMERA_STORAGE_PERMISSIONS = arrayOf(permission.READ_EXTERNAL_STORAGE,permission.WRITE_EXTERNAL_STORAGE, permission.CAMERA)
        var CAMERA_STORAGE_PERMISSIONS_10 = arrayOf(permission.ACCESS_MEDIA_LOCATION, permission.CAMERA)

        fun hasPermissions(activity: Activity, PERMISSIONS: Array<String>): Boolean {
        if (VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (permission in PERMISSIONS) {
                if (ActivityCompat.checkSelfPermission(activity,permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }
        return true
         }

        fun request_permissions(activity: Activity, PERMISSIONS: Array<String>) {
            ActivityCompat.requestPermissions(activity, PERMISSIONS, 1)
        }
    }

}