package com.aminfathi.nava

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.mutableStateOf
import com.aminfathi.nava.ui.*
import kotlinx.coroutines.launch

class MainActivity:FragmentActivity() {
    private val vm:AppViewModel by viewModels()
    private val locked=mutableStateOf(true)
    private var credentialSuccess:(()->Unit)?=null
    private val credentialLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback=credentialSuccess;credentialSuccess=null
        if(result.resultCode==Activity.RESULT_OK) {callback?.invoke();scheduleLock()}
    }
    private var lockEnabled=false
    private var initialized=false
    private val handler=Handler(Looper.getMainLooper())
    private val timer=Runnable { if(lockEnabled) { locked.value=true;vm.cancel() } }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        lifecycleScope.launch {
            vm.state.collect { state ->
                lockEnabled=state.preferences.lock
                if(state.loaded && !initialized) { initialized=true;locked.value=lockEnabled }
                if(!lockEnabled) locked.value=false
            }
        }
        setContent { NavaApp(vm,locked.value,::unlock,::setLock) }
    }
    private fun authenticate(success:()->Unit) {
        val flags=BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val context=localized(vm.state.value.preferences.language)
        if(Build.VERSION.SDK_INT<30) {
            val manager=getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION")
            val intent=manager.createConfirmDeviceCredentialIntent(context.label("unlock"),null)
            if(intent!=null) {credentialSuccess=success;credentialLauncher.launch(intent)}
            else android.widget.Toast.makeText(this,context.label("lock_unavailable"),android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if(BiometricManager.from(this).canAuthenticate(flags)!=BiometricManager.BIOMETRIC_SUCCESS) {
            android.widget.Toast.makeText(this,context.label("lock_unavailable"),android.widget.Toast.LENGTH_LONG).show();return
        }
        val prompt=BiometricPrompt(this,ContextCompat.getMainExecutor(this),object:BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) { success();scheduleLock() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(context.label("unlock")).setAllowedAuthenticators(flags).build())
    }
    private fun unlock()=authenticate { locked.value=false }
    private fun setLock(enabled:Boolean) { authenticate { vm.lock(enabled);locked.value=false } }
    private fun scheduleLock() { handler.removeCallbacks(timer);if(lockEnabled) handler.postDelayed(timer,120000) }
    override fun onUserInteraction() { super.onUserInteraction();scheduleLock() }
    override fun onStop() { super.onStop();vm.cancel();if(lockEnabled) locked.value=true;handler.removeCallbacks(timer) }
    override fun onDestroy() { handler.removeCallbacks(timer);super.onDestroy() }
}
