package com.infinity.drive.domain.model

/** Accent IDs also identify colours saved by previous releases. */
enum class AccentColour(
    val label: String,
    val light: Long,
    val dark: Long,
    private val tones: PresetTones? = null
) {
    BLUE("Blue",0xFF0068D9,0xFF82B5FF),
    INDIGO("Indigo",0xFF3F51B5,0xFFB9C4FF),
    VIOLET("Violet",0xFF7B1FA2,0xFFE1B5F5),
    PINK("Pink",0xFFAD1457,0xFFFFB1D2),
    RED("Red",0xFFB71C1C,0xFFFFB4AB),
    ORANGE("Orange",0xFF9C4400,0xFFFFB77B),
    AMBER("Amber",0xFF795900,0xFFFFD873),
    GREEN("Green",0xFF216D35,0xFF9CDBA6),
    TEAL("Teal",0xFF006B5E,0xFF80D5C5),
    CYAN("Cyan",0xFF00677D,0xFF78D2EB),
    CATPPUCCIN("Catppuccin", 0xFF465B91, 0xFFB4C0F2, PresetTones(0x775874, 0xE6B7D5, 0x42695E, 0xA3D6C2, 0xF3F0F7, 0x20202F)),
    AURORA("Aurora", 0xFF264DA1, 0xFF91B1FF, PresetTones(0x465E83, 0xA5C9EF, 0x47627C, 0xA3DED5, 0xF1F5FF, 0x0D1628)),
    CLOUDFLARE("Cloudflare", 0xFF9C4B08, 0xFFFFB56F, PresetTones(0x765941, 0xF1C29B, 0x88601F, 0xF4D08C, 0xFFF8EE, 0x23190F)),
    COTTON_CANDY("Cotton Candy", 0xFF963F73, 0xFFFAB0DC, PresetTones(0x435C94, 0xB9C9F8, 0x76538F, 0xDBB9F3, 0xFFF4FA, 0x251A29)),
    DOOM("Doom", 0xFFA52B31, 0xFFFF938B, PresetTones(0x785454, 0xE2BAB7, 0x854F2B, 0xF7B58B, 0xFFF5F1, 0x221415)),
    GREEN_APPLE("Green Apple", 0xFF42661D, 0xFFB8DB82, PresetTones(0x506642, 0xBED5A9, 0x456D57, 0xA3D8BF, 0xF5FAEC, 0x171F13)),
    GRUVBOX("Gruvbox", 0xFF885023, 0xFFF1AF70, PresetTones(0x666330, 0xC8C781, 0x456651, 0xA2CCA5, 0xF6E8C3, 0x282421)),
    KANAGAWA("Kanagawa", 0xFF3F5E83, 0xFFA1BAF0, PresetTones(0x735941, 0xD6A978, 0x52683C, 0xADCA85, 0xF1EBDD, 0x222331)),
    LAVENDER("Lavender", 0xFF685093, 0xFFD1BBF6, PresetTones(0x665884, 0xCBBCE2, 0x875675, 0xF3BDD9, 0xF9F4FF, 0x21192C)),
    MIDNIGHT("Midnight", 0xFF27498E, 0xFFB0C6FB, PresetTones(0x4F5C78, 0xA8BBD9, 0x445588, 0xA6AEF0, 0xF1F4FA, 0x0F1422)),
    MOCHA("Mocha", 0xFF775144, 0xFFDDB59E, PresetTones(0x665747, 0xD4C2A7, 0x775B58, 0xDEB9B1, 0xFBF3EB, 0x241B18)),
    STRAWBERRY("Strawberry", 0xFFA43051, 0xFFFCAFC1, PresetTones(0x775155, 0xE8B9BD, 0x6E5B23, 0xDCC780, 0xFFF3F4, 0x26171E)),
    TIDAL("Tidal", 0xFF006C62, 0xFF80D8C7, PresetTones(0x425E61, 0xA9CFD2, 0x365F7E, 0x9CCCEC, 0xEEF8F5, 0x142221)),
    NORD("Nord", 0xFF415D7E, 0xFF8EC2D2, PresetTones(0x536079, 0xBBC8DF, 0x755778, 0xDBBCDF, 0xEEF3F8, 0x293341)),
    ROSE_PINE("Rosé Pine", 0xFF755681, 0xFFD7B3E7, PresetTones(0x895260, 0xF3B9BE, 0x4B696B, 0xA8D3D3, 0xF8F0ED, 0x241E31)),
    TAKO("Tako", 0xFF48684B, 0xFFADD3A6, PresetTones(0x49616C, 0xA7C7D7, 0x6B5D33, 0xD7C68B, 0xF0F7EF, 0x18221C)),
    TOKYO_NIGHT("Tokyo Night", 0xFF3C5188, 0xFF9CB6F0, PresetTones(0x685588, 0xCBB8F1, 0x426C67, 0x9BD3C5, 0xEFF2FA, 0x1A1C2E)),
    YIN_YANG("Yin Yang", 0xFF46474A, 0xFFC4C8CE, PresetTones(0x55565A, 0xDBDDE0, 0x636569, 0xF0F0F1, 0xF6F6F6, 0x18191A)),
    YOTSUBA("Yotsuba", 0xFFA0492F, 0xFFFFB092, PresetTones(0x825449, 0xEDBDB0, 0x73602E, 0xE1C982, 0xFFF4EC, 0x261B18)),
    SAPPHIRE("Sapphire", 0xFF225CA2, 0xFF83BBF9, PresetTones(0x4F5694, 0xB8BFF5, 0x36668E, 0x95CEF4, 0xF0F6FE, 0x121D2D)),
    SUNSET("Sunset", 0xFFA34624, 0xFFFFAE8B, PresetTones(0x8C542A, 0xF5C084, 0x854E59, 0xEDABB9, 0xFFF2E7, 0x281A18)),
    OCEAN("Ocean", 0xFF00686F, 0xFF78D6E0, PresetTones(0x3F6281, 0xA3CDEC, 0x426D67, 0x9ED7C9, 0xEAF8FA, 0x122127)),
    FOREST("Forest", 0xFF2C603D, 0xFFA0D6AB, PresetTones(0x536834, 0xBAD38C, 0x6F6139, 0xD5C99F, 0xF2F6E9, 0x152119)),
    ROSE_GOLD("Rose Gold", 0xFF8C4957, 0xFFF0B2BE, PresetTones(0x845745, 0xE8BBAA, 0x785F44, 0xDFC6A2, 0xFFF4F1, 0x281A1C)),
    CORAL("Coral", 0xFFA33D3F, 0xFFFFA9A3, PresetTones(0x935333, 0xF7B88F, 0x82506B, 0xEAB6D7, 0xFFF1EE, 0x271718)),
    SLATE("Slate", 0xFF3F5B66, 0xFFA8C7D2, PresetTones(0x526372, 0xB7C9D8, 0x4C617E, 0xB3C7E6, 0xF0F5F6, 0x192227)),
    DRACULA("Dracula", 0xFF655092, 0xFFCBA9FC, PresetTones(0x934F7C, 0xF3A7D3, 0x34684F, 0x9CE6BD, 0xF6F2FA, 0x272536)),
    MONOCHROME("Monochrome", 0xFF212225, 0xFFE4E5E7, PresetTones(0x48494B, 0xBDBEBF, 0x5D5F62, 0xA2A3A5, 0xFAFAFA, 0x101112));

    val chooserLabel: String get() = if (this == BLUE) "Default" else label

    fun themeDefinition(): CustomTheme? = tones?.let {
        CustomTheme(
            id = name,
            name = chooserLabel,
            light = ThemePalette(light, it.secondaryLight.argb(), it.tertiaryLight.argb(), it.backgroundLight.argb()),
            dark = ThemePalette(dark, it.secondaryDark.argb(), it.tertiaryDark.argb(), it.backgroundDark.argb())
        )
    }

    private fun Int.argb(): Long = 0xFF000000L or toLong()

    companion object {
        val presets = listOf(
            BLUE, CATPPUCCIN, AURORA, CLOUDFLARE, COTTON_CANDY, DOOM, GREEN_APPLE, GRUVBOX,
            KANAGAWA, LAVENDER, MIDNIGHT, MOCHA, STRAWBERRY, TIDAL, NORD, ROSE_PINE, TAKO,
            TOKYO_NIGHT, YIN_YANG, YOTSUBA, SAPPHIRE, SUNSET, OCEAN, FOREST, ROSE_GOLD,
            VIOLET, AMBER, CORAL, SLATE, DRACULA, MONOCHROME
        )
    }
}

private data class PresetTones(
    val secondaryLight: Int,
    val secondaryDark: Int,
    val tertiaryLight: Int,
    val tertiaryDark: Int,
    val backgroundLight: Int,
    val backgroundDark: Int
)

enum class AppIconStyle(val label: String) {
    OUTLINE("Outline"),
    BROKEN("Broken")
}
enum class DashboardStyle(val label: String, val description: String) {
    CLASSIC("Classic", "A warm storage summary"),
    ORBIT("Orbit", "Storage in a ring"),
    BLOOM("Bloom", "File sizes by category"),
    RIBBON("Ribbon", "A ribbon of file types"),
    MIST("Mist", "A soft cloud summary"),
    STRATA("Strata", "File types in layers"),
    TIDE("Tide", "Storage share by type"),
    COVE("Cove", "Cloud and device storage"),
    BEACON("Beacon", "A warm storage total");

    companion object {
        fun fromStored(value: String?): DashboardStyle = storedStyle(value) ?: CLASSIC

        fun storedStyleNames(values: Set<String>): Set<String> =
            values.mapNotNullTo(mutableSetOf()) { storedStyle(it)?.name }

        private fun storedStyle(value: String?): DashboardStyle? = when (value) {
            "MOSAIC" -> BLOOM
            "AURORA", "HORIZON" -> MIST
            "SPECTRUM" -> TIDE
            "LEDGER" -> COVE
            "COMPACT" -> STRATA
            else -> entries.firstOrNull { it.name == value }
        }
    }
}
enum class CollectionRelock(val minutes:Int,val label:String) {
    AFTER_APP_EXIT(0,"After leaving the app"),
    NEVER(-1,"Never"),
    THREE(3,"After 3 minutes"), FIVE(5,"After 5 minutes"), TEN(10,"After 10 minutes"),
    FIFTEEN(15,"After 15 minutes"), THIRTY(30,"After 30 minutes");
    companion object { fun fromMinutes(value:Int)=entries.firstOrNull{it.minutes==value}?:AFTER_APP_EXIT }
}
