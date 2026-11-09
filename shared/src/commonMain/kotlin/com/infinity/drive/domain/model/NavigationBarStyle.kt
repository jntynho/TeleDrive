package com.infinity.drive.domain.model

enum class NavigationBarStyle(val supportsBlur:Boolean){
    STANDARD(true),FLOATING(true),MIUIX(false);
    val label:String get()=when(this){STANDARD->"Standard";FLOATING->"Floating";MIUIX->"Miuix"}
}
