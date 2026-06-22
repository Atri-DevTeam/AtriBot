package top.yzljc.atribot.configuration

/**
 * @Author YZ_Ljc_
 * @ClassName ResourcesProperties
 * @Created_at 2026/06/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.configuration
 */
object ResourcesProperties {

    // ==================== 图片 / 媒体资源 ====================

    // @ClassName RconHandler
    const val RCON_GUIDE_IMG = "https://res.yzljc.top/images/rcon-shower.png"

    // @ClassName Test, MusicCommand
    const val A_SILENT_MIRROR_MP3 = "https://res.yzljc.top/images/a_silent_mirror.mp3"

    // @ClassName MusicCommand
    const val BIOME_FEST_MP3 = "https://res.yzljc.top/images/c418.mp3"

    // @ClassName HypixelAnnouncements
    const val HYPIXEL_HEADER_IMG = "https://res.yzljc.top/images/hypixel-header.png"

    // @ClassName MinecraftCommand, VersionCheckImpl
    const val GRASS_BLOCK_IMG = "https://res.yzljc.top/images/grass-block-img.png"

    // @ClassName ElectricCheck
    const val TUFE_LOGO_IMG = "https://res.yzljc.top/images/tufe-logo.png"

    // @ClassName PlayerProfile, VerifyMinecraftCommand
    const val HOW_TO_VERIFY_GIF = "https://res.yzljc.top/images/how-to-verify.gif"

    // @ClassName FullMessageEnableCommand
    const val FULL_MESSAGE_GUIDE_IMG = "https://res.yzljc.top/images/full-message-guide.png"

    // @ClassName EventRecord
    const val WELCOME_IMG = "https://res.yzljc.top/images/welcome-img-w.png"

    // @ClassName MinecraftNetwork
    const val CONSOLE_LOGO_IMG = "https://res.yzljc.top/images/console-logo.png"

    // @ClassName DiceImpl
    const val SKB_LOGO_IMG = "https://res.yzljc.top/images/skb-logo.png"

    // @ClassName DiceImpl
    const val SKB_BANK_LOGO_IMG = "https://res.yzljc.top/images/skb-bank-logo.png"

    // @ClassName DiceImpl （模板，使用时将 <id> 替换为点数）
    const val DICE_RENDER_RESULT_IMG_T = "https://res.yzljc.top/images/dice_render_result_<id>.png"

    // @ClassName DiceImpl
    const val DICE_RENDER_RESULT_7_IMG = "https://res.yzljc.top/images/dice_render_result_7.png"

    // ==================== 接口 / API 资源 ====================

    // @ClassName MinecraftNews
    const val MCNEWS_API = "https://www.yzljc.top/data/api/v2/atribot/function/mcnews"

    // @ClassName Calendar(general), Calendar(task)
    const val CALENDAR_API = "https://www.yzljc.top/data/api/v2/atribot/function/calendar"

    // @ClassName HelpCommand
    const val HELP_API = "https://www.yzljc.top/data/api/v2/atribot/function/help"

    // @ClassName SponsorCommand
    const val SPONSORS_API = "https://www.yzljc.top/data/api/v2/atribot/function/sponsors"

    // @ClassName PreImageGenerate
    const val IMAGE_DUMP_API = "https://www.yzljc.top/data/api/v2/atribot/function/image-dump"

    // @ClassName HappyNewYear
    const val HAPPY_NEW_YEAR_API = "https://www.yzljc.top/data/api/v2/atribot/function/happy-new-year"

    // @ClassName MojangStatus
    const val MOJANG_STATUS_API = "https://www.yzljc.top/data/api/v2/atribot/function/mojang-status"

    // @ClassName Feedback
    const val FEEDBACK_SUBMIT_API = "https://www.yzljc.top/data/api/v2/open-api/feedback/submit"

    // @ClassName PlayerProfile （使用时拼接 key 与查询参数）
    const val PLAYER_CARD_API = "https://www.yzljc.top/data/api/v2/player/card/"

    // @ClassName PlayerProfile
    const val PLAYER_ACHIEVEMENTS_NAME_API = "https://www.yzljc.top/data/api/v2/player/achievements/name/{name}"

    // @ClassName PlayerProfile
    const val PLAYER_ACHIEVEMENTS_UUID_API = "https://www.yzljc.top/data/api/v2/player/achievements/uuid/{uuid}"

    // @ClassName PlayerProfile
    const val PLAYER_FRIENDS_NAME_API = "https://www.yzljc.top/data/api/v2/player/friends/name/{name}"

    // @ClassName PlayerProfile
    const val PLAYER_FRIENDS_UUID_API = "https://www.yzljc.top/data/api/v2/player/friends/uuid/{uuid}"

    // @ClassName PlayerProfile
    const val PLAYER_GAMESTATS_NAME_API = "https://www.yzljc.top/data/api/v2/player/gamestats/name/{name}"

    // @ClassName PlayerProfile
    const val PLAYER_GAMESTATS_UUID_API = "https://www.yzljc.top/data/api/v2/player/gamestats/uuid/{uuid}"

    // @ClassName PlayerProfile
    const val PLAYER_WEB_QUERY = "https://www.yzljc.top/mc/query/"

    // @ClassName VerifyMinecraftCommand （使用时将 {uuid} 替换为玩家 UUID）
    const val PLAYER_AVATAR_API = "https://www.yzljc.top/data/api/v1/avatar/{uuid}"

    // @ClassName ManosabaDate
    const val MANOSABA_DATE_IMG = "https://www.yzljc.top/data/api/v2/atribot/function/manosaba-date"

    // @ClassName WebhookServer
    const val COMMIT_DISPLAY_API = "https://www.yzljc.top/data/api/v2/atribot/function/commit-display"
}
