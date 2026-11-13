package com.infinity.drive.core.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.Collections
import java.util.IdentityHashMap

/** Count the app's visible activities, so an internal picker or rotation is not an app exit. */
class CollectionLockLifecycle(private val onForeground:()->Unit,private val onBackground:()->Unit):Application.ActivityLifecycleCallbacks {
    private val started=Collections.newSetFromMap(IdentityHashMap<Activity,Boolean>())
    private var foreground=false
    override fun onActivityStarted(activity:Activity){
        if(started.add(activity)&&!foreground){foreground=true;onForeground()}
    }
    override fun onActivityStopped(activity:Activity){
        if(started.remove(activity)&&started.isEmpty()&&!activity.isChangingConfigurations&&foreground){foreground=false;onBackground()}
    }
    override fun onActivityDestroyed(activity:Activity){onActivityStopped(activity)}
    override fun onActivityCreated(activity:Activity,savedInstanceState:Bundle?)=Unit
    override fun onActivityResumed(activity:Activity)=Unit
    override fun onActivityPaused(activity:Activity)=Unit
    override fun onActivitySaveInstanceState(activity:Activity,outState:Bundle)=Unit
}
