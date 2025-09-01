package com.cyberflux.qwinai.utils

import android.content.Context
import com.cyberflux.qwinai.model.MessageContentBlock
import java.util.regex.Pattern
import timber.log.Timber

/**
 * Comprehensive emoji processing system supporting Unicode emoji,
 * GitHub-style shortcodes, and custom emoji providers.
 * 
 * Features:
 * - :smile: → 😄 shortcode conversion
 * - Unicode emoji detection and parsing
 * - Custom emoji provider support
 * - Skin tone modifiers
 * - Emoji descriptions for accessibility
 */
class EmojiProcessor(
    private val context: Context,
    private val enableCustomProvider: Boolean = false,
    private val customProviderUrl: String? = null
) {
    
    companion object {
        private val SHORTCODE_PATTERN = Pattern.compile(":([a-zA-Z0-9+\\-_&.ô'Åéãíç]+):")
        private val UNICODE_EMOJI_PATTERN = Pattern.compile(
            "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+" + // Supplementary characters
            "|[\\u2600-\\u27FF]+" +              // Miscellaneous symbols
            "|[\\u2B00-\\u2BFF]+" +              // Additional symbols
            "|[\\uD83C][\\uDF00-\\uDFFF]+" +     // Emoji skin tones
            "|[\\uD83D][\\uDC00-\\uDFFF]+" +     // Emoticons
            "|[\\uD83E][\\uDD00-\\uDFFF]+"       // Additional emoticons
        )
        
        // Most commonly used emoji shortcodes mapped to Unicode
        private val EMOJI_MAP = mapOf(
            // Faces & People
            "smile" to "😄", "grin" to "😁", "joy" to "😂", "smiley" to "😃", "laughing" to "😆",
            "sweat_smile" to "😅", "rolling_on_the_floor_laughing" to "🤣", "rofl" to "🤣",
            "relaxed" to "☺️", "blush" to "😊", "innocent" to "😇", "slightly_smiling_face" to "🙂",
            "upside_down_face" to "🙃", "wink" to "😉", "relieved" to "😌", "heart_eyes" to "😍",
            "kissing_heart" to "😘", "kissing" to "😗", "kissing_smiling_eyes" to "😙",
            "kissing_closed_eyes" to "😚", "yum" to "😋", "stuck_out_tongue_winking_eye" to "😜",
            "zany_face" to "🤪", "stuck_out_tongue" to "😛", "stuck_out_tongue_closed_eyes" to "😝",
            "money_mouth_face" to "🤑", "nerd_face" to "🤓", "sunglasses" to "😎", "star_struck" to "🤩",
            "partying_face" to "🥳", "smirk" to "😏", "unamused" to "😒", "disappointed" to "😞",
            "pensive" to "😔", "worried" to "😟", "confused" to "😕", "slightly_frowning_face" to "🙁",
            "frowning_face" to "☹️", "persevere" to "😣", "confounded" to "😖", "tired_face" to "😫",
            "weary" to "😩", "pleading_face" to "🥺", "cry" to "😢", "sob" to "😭",
            "triumph" to "😤", "angry" to "😠", "rage" to "😡", "face_with_symbols_on_mouth" to "🤬",
            "exploding_head" to "🤯", "flushed" to "😳", "hot_face" to "🥵", "cold_face" to "🥶",
            "scream" to "😱", "fearful" to "😨", "cold_sweat" to "😰", "disappointed_relieved" to "😥",
            "sweat" to "😓", "hugs" to "🤗", "thinking" to "🤔", "face_with_hand_over_mouth" to "🤭",
            "shushing_face" to "🤫", "lying_face" to "🤥", "no_mouth" to "😶", "neutral_face" to "😐",
            "expressionless" to "😑", "grimacing" to "😬", "rolling_eyes" to "🙄", "hushed" to "😯",
            "frowning" to "😦", "anguished" to "😧", "open_mouth" to "😮", "astonished" to "😲",
            "yawning_face" to "🥱", "dizzy_face" to "😵", "face_with_spiral_eyes" to "😵‍💫",
            "mask" to "😷", "face_with_thermometer" to "🤒", "face_with_head_bandage" to "🤕",
            "nauseated_face" to "🤢", "vomiting_face" to "🤮", "sneezing_face" to "🤧",
            "sleeping" to "😴", "zzz" to "💤", "poop" to "💩", "smiling_imp" to "😈",
            "imp" to "👿", "japanese_ogre" to "👹", "japanese_goblin" to "👺", "skull" to "💀",
            "ghost" to "👻", "alien" to "👽", "robot" to "🤖", "smiley_cat" to "😺",
            "smile_cat" to "😸", "joy_cat" to "😹", "heart_eyes_cat" to "😻", "smirk_cat" to "😼",
            "kissing_cat" to "😽", "scream_cat" to "🙀", "crying_cat_face" to "😿", "pouting_cat" to "😾",
            
            // Hand gestures
            "wave" to "👋", "raised_back_of_hand" to "🤚", "raised_hand_with_fingers_splayed" to "🖐️",
            "hand" to "✋", "raised_hand" to "✋", "vulcan_salute" to "🖖", "ok_hand" to "👌",
            "pinched_fingers" to "🤌", "pinching_hand" to "🤏", "v" to "✌️", "crossed_fingers" to "🤞",
            "love_you_gesture" to "🤟", "metal" to "🤘", "call_me_hand" to "🤙", "point_left" to "👈",
            "point_right" to "👉", "point_up_2" to "👆", "middle_finger" to "🖕", "point_down" to "👇",
            "point_up" to "☝️", "raised_fist" to "✊", "fist" to "👊", "fist_oncoming" to "👊",
            "facepunch" to "👊", "punch" to "👊", "fist_left" to "🤛", "fist_right" to "🤜",
            "thumbsup" to "👍", "+1" to "👍", "thumbsdown" to "👎", "-1" to "👎",
            "clap" to "👏", "raised_hands" to "🙌", "open_hands" to "👐", "palms_up_together" to "🤲",
            "handshake" to "🤝", "pray" to "🙏", "nail_care" to "💅", "selfie" to "🤳",
            
            // Hearts and symbols
            "heart" to "❤️", "orange_heart" to "🧡", "yellow_heart" to "💛", "green_heart" to "💚",
            "blue_heart" to "💙", "purple_heart" to "💜", "brown_heart" to "🤎", "black_heart" to "🖤",
            "white_heart" to "🤍", "broken_heart" to "💔", "heart_exclamation" to "❣️",
            "two_hearts" to "💕", "revolving_hearts" to "💞", "heartbeat" to "💓",
            "heartpulse" to "💗", "sparkling_heart" to "💖", "cupid" to "💘", "gift_heart" to "💝",
            "heart_decoration" to "💟", "peace_symbol" to "☮️", "latin_cross" to "✝️",
            "star_and_crescent" to "☪️", "om" to "🕉️", "wheel_of_dharma" to "☸️", "star_of_david" to "✡️",
            "six_pointed_star" to "🔯", "menorah" to "🕎", "yin_yang" to "☯️", "orthodox_cross" to "☦️",
            "place_of_worship" to "🛐", "ophiuchus" to "⛎", "aries" to "♈", "taurus" to "♉",
            "gemini" to "♊", "cancer" to "♋", "leo" to "♌", "virgo" to "♍", "libra" to "♎",
            "scorpius" to "♏", "sagittarius" to "♐", "capricorn" to "♑", "aquarius" to "♒",
            "pisces" to "♓", "id" to "🆔", "atom_symbol" to "⚛️", "accept" to "🉑",
            "radioactive" to "☢️", "biohazard" to "☣️", "mobile_phone_off" to "📴",
            "vibration_mode" to "📳", "u6709" to "🈶", "u7121" to "🈚", "u7533" to "🈸",
            "u55b6" to "🈺", "u6708" to "🈷️", "eight_pointed_black_star" to "✴️",
            "vs" to "🆚", "white_flower" to "💮", "ideograph_advantage" to "🉐",
            "secret" to "㊙️", "congratulations" to "㊗️", "u5408" to "🈴", "u6e80" to "🈵",
            "u5272" to "🈹", "u7981" to "🈲", "a" to "🅰️", "b" to "🅱️", "ab" to "🆎",
            "cl" to "🆑", "o2" to "🅾️", "sos" to "🆘", "x" to "❌", "o" to "⭕",
            "stop_sign" to "🛑", "no_entry" to "⛔", "name_badge" to "📛", "no_entry_sign" to "🚫",
            "100" to "💯", "anger" to "💢", "hotsprings" to "♨️", "no_pedestrians" to "🚷",
            "do_not_litter" to "🚯", "no_bicycles" to "🚳", "non-potable_water" to "🚱",
            "underage" to "🔞", "no_mobile_phones" to "📵", "exclamation" to "❗", "grey_exclamation" to "❕",
            "question" to "❓", "grey_question" to "❔", "bangbang" to "‼️", "interrobang" to "⁉️",
            "low_brightness" to "🔅", "high_brightness" to "🔆", "part_alternation_mark" to "〽️",
            "warning" to "⚠️", "children_crossing" to "🚸", "trident" to "🔱", "fleur_de_lis" to "⚜️",
            "beginner" to "🔰", "recycle" to "♻️", "white_check_mark" to "✅", "u6307" to "🈯",
            "chart" to "💹", "sparkle" to "❇️", "eight_spoked_asterisk" to "✳️", "negative_squared_cross_mark" to "❎",
            "globe_with_meridians" to "🌐", "diamond_shape_with_a_dot_inside" to "💠", "m" to "Ⓜ️",
            "cyclone" to "🌀", "zzz" to "💤", "atm" to "🏧", "wc" to "🚾", "wheelchair" to "♿",
            "parking" to "🅿️", "u7a7a" to "🈳", "sa" to "🈂️", "passport_control" to "🛂",
            "customs" to "🛃", "baggage_claim" to "🛄", "left_luggage" to "🛅", "elevator" to "🛗",
            
            // Numbers and letters
            "zero" to "0️⃣", "one" to "1️⃣", "two" to "2️⃣", "three" to "3️⃣", "four" to "4️⃣",
            "five" to "5️⃣", "six" to "6️⃣", "seven" to "7️⃣", "eight" to "8️⃣", "nine" to "9️⃣",
            "keycap_ten" to "🔟", "1234" to "🔢", "hash" to "#️⃣", "asterisk" to "*️⃣",
            
            // Activity and objects
            "soccer" to "⚽", "baseball" to "⚾", "softball" to "🥎", "basketball" to "🏀",
            "volleyball" to "🏐", "football" to "🏈", "rugby_football" to "🏉", "tennis" to "🎾",
            "flying_disc" to "🥏", "bowling" to "🎳", "cricket_game" to "🏏", "field_hockey" to "🏑",
            "ice_hockey" to "🏒", "lacrosse" to "🥍", "ping_pong" to "🏓", "badminton" to "🏸",
            "boxing_glove" to "🥊", "martial_arts_uniform" to "🥋", "goal_net" to "🥅",
            "golf" to "⛳", "ice_skate" to "⛸️", "fishing_pole_and_fish" to "🎣", "diving_mask" to "🤿",
            "running_shirt_with_sash" to "🎽", "ski" to "🎿", "sled" to "🛷", "curling_stone" to "🥌",
            
            // Food and drink
            "coffee" to "☕", "tea" to "🍵", "sake" to "🍶", "baby_bottle" to "🍼",
            "beer" to "🍺", "beers" to "🍻", "clinking_glasses" to "🥂", "wine_glass" to "🍷",
            "tumbler_glass" to "🥃", "cocktail" to "🍸", "tropical_drink" to "🍹",
            "champagne" to "🍾", "bottle_with_popping_cork" to "🍾", "ice_cube" to "🧊",
            "spoon" to "🥄", "fork_and_knife" to "🍴", "plate_with_cutlery" to "🍽️",
            "bowl_with_spoon" to "🥣", "takeout_box" to "🥡", "chopsticks" to "🥢",
            "salt" to "🧂", "pizza" to "🍕", "hamburger" to "🍔", "fries" to "🍟",
            "hotdog" to "🌭", "sandwich" to "🥪", "taco" to "🌮", "burrito" to "🌯",
            "stuffed_flatbread" to "🥙", "falafel" to "🧆", "egg" to "🥚", "fried_egg" to "🍳",
            "shallow_pan_of_food" to "🥘", "stew" to "🍲", "fondue" to "🫕", "bowl_with_spoon" to "🥣",
            "green_salad" to "🥗", "popcorn" to "🍿", "butter" to "🧈", "canned_food" to "🥫",
            "bento" to "🍱", "rice_cracker" to "🍘", "rice_ball" to "🍙", "rice" to "🍚",
            "curry" to "🍛", "ramen" to "🍜", "spaghetti" to "🍝", "sweet_potato" to "🍠",
            "oden" to "🍢", "sushi" to "🍣", "fried_shrimp" to "🍤", "fish_cake" to "🍥",
            "moon_cake" to "🥮", "dango" to "🍡", "dumpling" to "🥟", "fortune_cookie" to "🥠",
            "cookie" to "🍪", "birthday" to "🎂", "cake" to "🍰", "cupcake" to "🧁",
            "pie" to "🥧", "chocolate_bar" to "🍫", "candy" to "🍬", "lollipop" to "🍭",
            "custard" to "🍮", "honey_pot" to "🍯", "apple" to "🍎", "green_apple" to "🍏",
            "pear" to "🍐", "tangerine" to "🍊", "lemon" to "🍋", "banana" to "🍌",
            "watermelon" to "🍉", "grapes" to "🍇", "strawberry" to "🍓", "melon" to "🍈",
            "cherries" to "🍒", "peach" to "🍑", "mango" to "🥭", "pineapple" to "🍍",
            "coconut" to "🥥", "kiwi_fruit" to "🥝", "tomato" to "🍅", "eggplant" to "🍆",
            "avocado" to "🥑", "broccoli" to "🥦", "leafy_greens" to "🥬", "cucumber" to "🥒",
            "hot_pepper" to "🌶️", "bell_pepper" to "🫑", "corn" to "🌽", "carrot" to "🥕",
            "olive" to "🫒", "garlic" to "🧄", "onion" to "🧅", "mushroom" to "🍄",
            "peanuts" to "🥜", "chestnut" to "🌰", "bread" to "🍞", "croissant" to "🥐",
            "baguette_bread" to "🥖", "flatbread" to "🫓", "pretzel" to "🥨", "bagel" to "🥯",
            "pancakes" to "🥞", "waffle" to "🧇", "cheese" to "🧀", "meat_on_bone" to "🍖",
            "poultry_leg" to "🍗", "cut_of_meat" to "🥩", "bacon" to "🥓", "lobster" to "🦞",
            "shrimp" to "🦐", "squid" to "🦑", "oyster" to "🦪", "icecream" to "🍦",
            "shaved_ice" to "🍧", "ice_cream" to "🍨", "doughnut" to "🍩", "donut" to "🍩",
            
            // Animals and nature
            "dog" to "🐶", "cat" to "🐱", "mouse" to "🐭", "hamster" to "🐹", "rabbit" to "🐰",
            "fox_face" to "🦊", "bear" to "🐻", "panda_face" to "🐼", "koala" to "🐨",
            "tiger" to "🐯", "lion" to "🦁", "cow" to "🐮", "pig" to "🐷", "pig_nose" to "🐽",
            "frog" to "🐸", "squid" to "🦑", "octopus" to "🐙", "shrimp" to "🦐", "monkey_face" to "🐵",
            "gorilla" to "🦍", "orangutan" to "🦧", "dog2" to "🐕", "poodle" to "🐩",
            "guide_dog" to "🦮", "service_dog" to "🐕‍🦺", "cat2" to "🐈", "black_cat" to "🐈‍⬛",
            "lion_face" to "🦁", "tiger2" to "🐅", "leopard" to "🐆", "horse" to "🐴",
            "racehorse" to "🐎", "unicorn" to "🦄", "zebra" to "🦓", "deer" to "🦌",
            "bison" to "🦬", "cow2" to "🐄", "ox" to "🐂", "water_buffalo" to "🐃",
            "pig2" to "🐖", "boar" to "🐗", "sheep" to "🐑", "ram" to "🐏", "goat" to "🐐",
            "dromedary_camel" to "🐪", "camel" to "🐫", "llama" to "🦙", "giraffe" to "🦒",
            "elephant" to "🐘", "mammoth" to "🦣", "rhinoceros" to "🦏", "hippopotamus" to "🦛",
            "mouse2" to "🐁", "rat" to "🐀", "hamster2" to "🐹", "rabbit2" to "🐇",
            "chipmunk" to "🐿️", "beaver" to "🦫", "hedgehog" to "🦔", "bat" to "🦇",
            "bear2" to "🐻", "polar_bear" to "🐻‍❄️", "koala2" to "🐨", "panda" to "🐼",
            "sloth" to "🦥", "otter" to "🦦", "skunk" to "🦨", "kangaroo" to "🦘",
            "badger" to "🦡", "feet" to "🐾", "turkey" to "🦃", "chicken" to "🐔",
            "rooster" to "🐓", "hatching_chick" to "🐣", "baby_chick" to "🐤", "hatched_chick" to "🐥",
            "bird" to "🐦", "penguin" to "🐧", "dove" to "🕊️", "eagle" to "🦅",
            "duck" to "🦆", "swan" to "🦢", "owl" to "🦉", "dodo" to "🦤",
            "feather" to "🪶", "flamingo" to "🦩", "peacock" to "🦚", "parrot" to "🦜",
            
            // Travel and places
            "airplane" to "✈️", "small_airplane" to "🛩️", "flight_departure" to "🛫",
            "flight_arrival" to "🛬", "parachute" to "🪂", "seat" to "💺", "helicopter" to "🚁",
            "suspension_railway" to "🚟", "mountain_cableway" to "🚠", "aerial_tramway" to "🚡",
            "satellite" to "🛰️", "rocket" to "🚀", "flying_saucer" to "🛸", "bellhop_bell" to "🛎️",
            "luggage" to "🧳", "hourglass" to "⌛", "hourglass_flowing_sand" to "⏳",
            "watch" to "⌚", "alarm_clock" to "⏰", "stopwatch" to "⏱️", "timer_clock" to "⏲️",
            "mantelpiece_clock" to "🕰️", "clock12" to "🕐", "clock1230" to "🕧", "clock1" to "🕐",
            "clock130" to "🕜", "clock2" to "🕑", "clock230" to "🕝", "clock3" to "🕒",
            "clock330" to "🕞", "clock4" to "🕓", "clock430" to "🕟", "clock5" to "🕔",
            "clock530" to "🕠", "clock6" to "🕕", "clock630" to "🕡", "clock7" to "🕖",
            "clock730" to "🕢", "clock8" to "🕗", "clock830" to "🕣", "clock9" to "🕘",
            "clock930" to "🕤", "clock10" to "🕙", "clock1030" to "🕥", "clock11" to "🕚",
            "clock1130" to "🕦", "new_moon" to "🌑", "waxing_crescent_moon" to "🌒",
            "first_quarter_moon" to "🌓", "moon" to "🌔", "waxing_gibbous_moon" to "🌔",
            "full_moon" to "🌕", "waning_gibbous_moon" to "🌖", "last_quarter_moon" to "🌗",
            "waning_crescent_moon" to "🌘", "crescent_moon" to "🌙", "new_moon_with_face" to "🌚",
            "first_quarter_moon_with_face" to "🌛", "last_quarter_moon_with_face" to "🌜",
            "thermometer" to "🌡️", "sunny" to "☀️", "full_moon_with_face" to "🌝",
            "sun_with_face" to "🌞", "ringed_planet" to "🪐", "star" to "⭐",
            "star2" to "🌟", "stars" to "🌠", "milky_way" to "🌌", "cloud" to "☁️",
            "partly_sunny" to "⛅", "cloud_with_lightning_and_rain" to "⛈️",
            "sun_behind_small_cloud" to "🌤️", "sun_behind_large_cloud" to "🌥️",
            "sun_behind_rain_cloud" to "🌦️", "cloud_with_rain" to "🌧️",
            "cloud_with_snow" to "🌨️", "cloud_with_lightning" to "🌩️", "tornado" to "🌪️",
            "fog" to "🌫️", "wind_face" to "🌬️", "cyclone" to "🌀", "rainbow" to "🌈",
            "closed_umbrella" to "🌂", "open_umbrella" to "☂️", "umbrella" to "☔",
            "parasol_on_ground" to "⛱️", "high_voltage" to "⚡", "snowflake" to "❄️",
            "snowman" to "☃️", "snowman_with_snow" to "⛄", "comet" to "☄️",
            "fire" to "🔥", "droplet" to "💧", "ocean" to "🌊", "jack_o_lantern" to "🎃",
            "christmas_tree" to "🎄", "fireworks" to "🎆", "sparkler" to "🎇",
            "firecracker" to "🧨", "sparkles" to "✨", "balloon" to "🎈",
            "tada" to "🎉", "confetti_ball" to "🎊", "tanabata_tree" to "🎋",
            "bamboo" to "🎍", "dolls" to "🎎", "flags" to "🎏", "wind_chime" to "🎐",
            "rice_scene" to "🎑", "red_envelope" to "🧧", "ribbon" to "🎀",
            "gift" to "🎁", "reminder_ribbon" to "🎗️", "tickets" to "🎟️", "ticket" to "🎫",
            
            // Tech and objects
            "iphone" to "📱", "calling" to "📲", "phone" to "☎️", "telephone_receiver" to "📞",
            "pager" to "📟", "fax" to "📠", "battery" to "🔋", "electric_plug" to "🔌",
            "computer" to "💻", "desktop_computer" to "🖥️", "printer" to "🖨️",
            "keyboard" to "⌨️", "computer_mouse" to "🖱️", "trackball" to "🖲️",
            "minidisc" to "💽", "floppy_disk" to "💾", "cd" to "💿", "dvd" to "📀",
            "abacus" to "🧮", "movie_camera" to "🎥", "film_strip" to "🎞️",
            "film_projector" to "📽️", "clapper" to "🎬", "tv" to "📺", "camera" to "📷",
            "camera_flash" to "📸", "video_camera" to "📹", "vhs" to "📼",
            "mag" to "🔍", "mag_right" to "🔎", "candle" to "🕯️", "bulb" to "💡",
            "flashlight" to "🔦", "izakaya_lantern" to "🏮", "diya_lamp" to "🪔",
            "notebook_with_decorative_cover" to "📔", "closed_book" to "📕", "book" to "📖",
            "open_book" to "📖", "green_book" to "📗", "blue_book" to "📘",
            "orange_book" to "📙", "books" to "📚", "notebook" to "📓", "ledger" to "📒",
            "page_with_curl" to "📃", "scroll" to "📜", "page_facing_up" to "📄",
            "newspaper" to "📰", "rolled_up_newspaper" to "🗞️", "bookmark_tabs" to "📑",
            "bookmark" to "🔖", "moneybag" to "💰", "coin" to "🪙", "yen" to "💴",
            "dollar" to "💵", "euro" to "💶", "pound" to "💷", "money_with_wings" to "💸",
            "credit_card" to "💳", "receipt" to "🧾", "chart" to "💹",
            "email" to "✉️", "e-mail" to "📧", "incoming_envelope" to "📨",
            "envelope_with_arrow" to "📩", "outbox_tray" to "📤", "inbox_tray" to "📥",
            "package" to "📦", "mailbox" to "📫", "mailbox_closed" to "📪",
            "mailbox_with_mail" to "📬", "mailbox_with_no_mail" to "📭", "postbox" to "📮",
            "postal_horn" to "📯", "scroll" to "📜", "pencil2" to "✏️", "black_nib" to "✒️",
            "fountain_pen" to "🖋️", "pen" to "🖊️", "paintbrush" to "🖌️", "crayon" to "🖍️",
            "memo" to "📝", "pencil" to "📝", "briefcase" to "💼", "file_folder" to "📁",
            "open_file_folder" to "📂", "card_index_dividers" to "🗂️", "date" to "📅",
            "calendar" to "📆", "spiral_notepad" to "🗒️", "spiral_calendar" to "🗓️",
            "card_index" to "📇", "chart_with_upwards_trend" to "📈",
            "chart_with_downwards_trend" to "📉", "bar_chart" to "📊", "clipboard" to "📋",
            "pushpin" to "📌", "round_pushpin" to "📍", "paperclip" to "📎",
            "paperclips" to "🖇️", "straight_ruler" to "📏", "triangular_ruler" to "📐",
            "scissors" to "✂️", "card_file_box" to "🗃️", "file_cabinet" to "🗄️",
            "wastebasket" to "🗑️", "lock" to "🔒", "unlock" to "🔓", "lock_with_ink_pen" to "🔏",
            "closed_lock_with_key" to "🔐", "key" to "🔑", "old_key" to "🗝️",
            "hammer" to "🔨", "axe" to "🪓", "pick" to "⛏️", "hammer_and_pick" to "⚒️",
            "hammer_and_wrench" to "🛠️", "dagger" to "🗡️", "crossed_swords" to "⚔️",
            "gun" to "🔫", "boomerang" to "🪃", "bow_and_arrow" to "🏹", "shield" to "🛡️",
            "carpentry_saw" to "🪚", "wrench" to "🔧", "screwdriver" to "🪛",
            "nut_and_bolt" to "🔩", "gear" to "⚙️", "clamp" to "🗜️", "balance_scale" to "⚖️",
            "probing_cane" to "🦯", "link" to "🔗", "chains" to "⛓️", "hook" to "🪝",
            "toolbox" to "🧰", "magnet" to "🧲", "ladder" to "🪜", "alembic" to "⚗️",
            "test_tube" to "🧪", "petri_dish" to "🧫", "dna" to "🧬", "microscope" to "🔬",
            "telescope" to "🔭", "satellite" to "📡", "syringe" to "💉", "drop_of_blood" to "🩸",
            "pill" to "💊", "adhesive_bandage" to "🩹", "stethoscope" to "🩺", "door" to "🚪",
            "elevator" to "🛗", "mirror" to "🪞", "window" to "🪟", "bed" to "🛏️",
            "couch_and_lamp" to "🛋️", "chair" to "🪑", "toilet" to "🚽", "plunger" to "🪠",
            "shower" to "🚿", "bathtub" to "🛁", "mouse_trap" to "🪤", "razor" to "🪒",
            "lotion_bottle" to "🧴", "safety_pin" to "🧷", "broom" to "🧹",
            "basket" to "🧺", "roll_of_paper" to "🧻", "bucket" to "🪣", "soap" to "🧼",
            "toothbrush" to "🪥", "sponge" to "🧽", "fire_extinguisher" to "🧯",
            "shopping_cart" to "🛒", "smoking" to "🚬", "coffin" to "⚰️", "headstone" to "🪦",
            "funeral_urn" to "⚱️", "moyai" to "🗿", "placard" to "🪧"
        )
    }
    
    /**
     * Process content and replace emoji shortcodes with Unicode characters.
     */
    fun processEmojis(content: String): String {
        if (content.isBlank()) return content
        
        return try {
            val matcher = SHORTCODE_PATTERN.matcher(content)
            val result = StringBuffer()
            
            while (matcher.find()) {
                val shortcode = matcher.group(1)?.lowercase()
                val emoji = EMOJI_MAP[shortcode]
                
                if (emoji != null) {
                    matcher.appendReplacement(result, emoji)
                    Timber.d("✅ Converted :$shortcode: → $emoji")
                } else {
                    // Keep original shortcode if not found
                    matcher.appendReplacement(result, matcher.group())
                    Timber.d("⚠️ Unknown emoji shortcode: :$shortcode:")
                }
            }
            
            matcher.appendTail(result)
            result.toString()
        } catch (e: Exception) {
            Timber.e(e, "Failed to process emoji shortcodes")
            content
        }
    }
    
    /**
     * Extract emoji information from content for accessibility.
     */
    fun extractEmojiInfo(content: String): List<EmojiInfo> {
        val emojiList = mutableListOf<EmojiInfo>()
        
        try {
            // Find Unicode emoji
            val unicodeMatcher = UNICODE_EMOJI_PATTERN.matcher(content)
            while (unicodeMatcher.find()) {
                val unicode = unicodeMatcher.group()
                val description = getEmojiDescription(unicode)
                emojiList.add(EmojiInfo(unicode, unicode, description))
            }
            
            // Find shortcode references 
            val shortcodeMatcher = SHORTCODE_PATTERN.matcher(content)
            while (shortcodeMatcher.find()) {
                val shortcode = shortcodeMatcher.group(1)?.lowercase() ?: continue
                val unicode = EMOJI_MAP[shortcode]
                if (unicode != null) {
                    val description = getEmojiDescription(unicode)
                    emojiList.add(EmojiInfo(shortcode, unicode, description))
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract emoji info")
        }
        
        return emojiList.distinctBy { it.unicode }
    }
    
    /**
     * Get human-readable description for accessibility.
     */
    private fun getEmojiDescription(unicode: String): String {
        // Reverse lookup for description
        val shortcode = EMOJI_MAP.entries.find { it.value == unicode }?.key
        return when (shortcode) {
            "smile", "grin", "joy", "smiley" -> "smiling face"
            "heart", "orange_heart", "yellow_heart" -> "heart symbol"
            "thumbsup", "+1" -> "thumbs up"
            "thumbsdown", "-1" -> "thumbs down"
            "fire" -> "fire symbol"
            "100" -> "hundred points"
            "warning" -> "warning sign"
            "check_mark", "white_check_mark" -> "check mark"
            "x", "negative_squared_cross_mark" -> "cross mark"
            else -> shortcode?.replace("_", " ") ?: "emoji symbol"
        }
    }
    
    /**
     * Check if text contains emoji shortcodes.
     */
    fun containsEmojiShortcodes(text: String): Boolean {
        return SHORTCODE_PATTERN.matcher(text).find()
    }
    
    /**
     * Check if text contains Unicode emoji.
     */
    fun containsUnicodeEmoji(text: String): Boolean {
        return UNICODE_EMOJI_PATTERN.matcher(text).find()
    }
    
    /**
     * Get list of supported shortcodes (for autocomplete).
     */
    fun getSupportedShortcodes(): List<String> {
        return EMOJI_MAP.keys.sorted()
    }
    
    /**
     * Search emoji by keyword.
     */
    fun searchEmoji(query: String): List<EmojiSearchResult> {
        val results = mutableListOf<EmojiSearchResult>()
        val lowerQuery = query.lowercase()
        
        EMOJI_MAP.forEach { (shortcode, unicode) ->
            val description = getEmojiDescription(unicode)
            val score = calculateRelevanceScore(lowerQuery, shortcode, description)
            
            if (score > 0) {
                results.add(EmojiSearchResult(shortcode, unicode, description, score))
            }
        }
        
        return results.sortedByDescending { it.relevanceScore }.take(20)
    }
    
    private fun calculateRelevanceScore(query: String, shortcode: String, description: String): Int {
        return when {
            shortcode.equals(query, ignoreCase = true) -> 100 // Exact match
            shortcode.startsWith(query, ignoreCase = true) -> 80 // Starts with
            shortcode.contains(query, ignoreCase = true) -> 60 // Contains in shortcode
            description.contains(query, ignoreCase = true) -> 40 // Contains in description
            else -> 0 // No match
        }
    }
}

/**
 * Emoji information for accessibility and processing.
 */
data class EmojiInfo(
    val shortcode: String,
    val unicode: String,
    val description: String
)

/**
 * Emoji search result with relevance scoring.
 */
data class EmojiSearchResult(
    val shortcode: String,
    val unicode: String,
    val description: String,
    val relevanceScore: Int
)