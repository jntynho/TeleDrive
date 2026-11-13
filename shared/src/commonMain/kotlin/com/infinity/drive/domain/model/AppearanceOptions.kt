package com.infinity.drive.domain.model

/** Explicit pairs keep accent text readable in both bright and dark appearances. */
enum class AccentColour(val label:String,val light:Long,val dark:Long) {
    BLUE("Blue",0xFF0068D9,0xFF82B5FF),
    INDIGO("Indigo",0xFF3F51B5,0xFFB9C4FF),
    VIOLET("Violet",0xFF7B1FA2,0xFFE1B5F5),
    PINK("Pink",0xFFAD1457,0xFFFFB1D2),
    RED("Red",0xFFB71C1C,0xFFFFB4AB),
    ORANGE("Orange",0xFF9C4400,0xFFFFB77B),
    AMBER("Amber",0xFF795900,0xFFFFD873),
    GREEN("Green",0xFF216D35,0xFF9CDBA6),
    TEAL("Teal",0xFF006B5E,0xFF80D5C5),
    CYAN("Cyan",0xFF00677D,0xFF78D2EB)
}
enum class DashboardStyle(val label:String,val description:String) {
    CLASSIC("Classic","Warm paper and a simple storage summary"),
    ORBIT("Orbit","A balanced ring with your library at its centre"),
    MOSAIC("Mosaic","A clear breakdown of photos, videos, audio and files"),
    RIBBON("Ribbon","A flowing storage ribbon and compact totals"),
    AURORA("Aurora","Soft colour, calm contrast and a spacious summary"),
    COMPACT("Compact","A refined horizontal card with the essentials")
}
enum class CollectionRelock(val minutes:Int,val label:String) {
    AFTER_APP_EXIT(0,"After leaving the app"),
    NEVER(-1,"Never"),
    THREE(3,"After 3 minutes"), FIVE(5,"After 5 minutes"), TEN(10,"After 10 minutes"),
    FIFTEEN(15,"After 15 minutes"), THIRTY(30,"After 30 minutes");
    companion object { fun fromMinutes(value:Int)=entries.firstOrNull{it.minutes==value}?:AFTER_APP_EXIT }
}
