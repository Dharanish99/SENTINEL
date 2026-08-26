package com.dharanish.spoofguarddialer.ui.debug

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel

class DialerRoleDebugViewModel(application: Application) : AndroidViewModel(application) {

    private val roleManager: RoleManager? =
        getApplication<Application>().getSystemService(RoleManager::class.java)

    var roleAvailable by mutableStateOf(false)
        private set
    var roleHeld by mutableStateOf(false)
        private set
    var permissionsGranted by mutableStateOf(false)
        private set

    fun requiredPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    fun refreshStatus() {
        roleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) ?: false
        roleHeld = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
        permissionsGranted = requiredPermissions().all {
            ContextCompat.checkSelfPermission(getApplication(), it) == PackageManager.PERMISSION_GRANTED
        }
    }
}