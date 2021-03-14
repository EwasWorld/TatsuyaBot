package BotFrameworkBox;

import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;

import java.util.*;


/**
 * created: 1/10/18 alias lookups: https://github.com/vdurmont/emoji-java
 */
public enum Emoji {
    /*
     * Misc
     */
    POOP("poop", "\uD83D\uDCA9"),
    X("x", "❌"),
    MONEYBAG("moneybag", "💰"),
    GEM("gem", "\uD83D\uDC8E"),
    SCALES("scales", "⚖"),
    VULCAN("vulcan", "vulcan_salute", "\uD83D\uDD96"),
    CLOCK("clock4", "\uD83D\uDD53"),

    /*
     * Hands
     */
    OK_HAND("ok_hand", "\uD83D\uDC4C"),
    PUNCH("punch", "\uD83D\uDC4A"),
    RAISED_HAND("raised_hand", "✋"),
    METAL("metal", "heavy_metal", "\uD83E\uDD18"),
    WAVE("wave", "\uD83D\uDC4B"),
    ONE_FINGER("point_up", "☝"),
    THUMBS_UP("thumbsup", "\uD83D\uDC4D"),
    THUMBS_DOWN("thumbsdown", "\uD83D\uDC4E"),

    /*
     * Faces
     */
    THINKING("thinking", "\uD83E\uDD14"),
    NO_MOUTH("no_mouth", "\uD83D\uDE36"),
    ZIPPER_MOUTH("zipper_mouth", "\uD83E\uDD10"),

    /*
     * Card Suits
     */
    HEARTS("hearts", "♥"),
    CLUBS("clubs", "♣"),
    DIAMONDS("diamonds", "♦"),
    SPADES("spades", "♠"),

    /*
     * Numbers
     */
    ZERO("zero", "0⃣ "),
    ONE("one", "1⃣ "),
    TWO("two", "2⃣ "),
    THREE("three", "3⃣ "),
    FOUR("four", "4⃣ "),
    FIVE("five", "5⃣ "),
    SIX("six", "6⃣ "),
    SEVEN("seven", "7⃣ "),
    EIGHT("eight", "8⃣ "),
    NINE("nine", "9⃣ "),
    TEN("keycap_ten", "\uD83D\uDD1F"),

    /*
     * Music Player
     */
    PLAY("arrow_forward", "▶"),
    PAUSE("double_vertical_bar", "⏸"),
    STOP("black_square_for_stop", "⏹"),
    SKIP("fast_forward", "⏩"),
    RESET("arrows_counterclockwise", "\uD83D\uDD04"),
    UP_ARROW("arrow_up_small", "\uD83D\uDD3C"),
    DOWN_ARROW("arrow_down_small", "\uD83D\uDD3D"),
    DOUBLE_UP_ARROW("arrow_double_up", "⏫"),
    DOUBLE_DOWN_ARROW("arrow_double_down", "⏬"),

    /*
     * People
     */
    PERSON_HAND_RAISED("raising_hand", "\uD83D\uDE4B"),
    PERSON_NO_HANDS("no_good", "\uD83D\uDE45"),

    /*
     * Letters
     */
    LETTER_A(":regional_indicator_a:", ":regional_indicator_symbol_a:", "🇦"),
    LETTER_B(":regional_indicator_b:", ":regional_indicator_symbol_b:", "🇧"),
    LETTER_C(":regional_indicator_c:", ":regional_indicator_symbol_c:", "🇨"),
    LETTER_D(":regional_indicator_d:", ":regional_indicator_symbol_d:", "🇩"),
    LETTER_E(":regional_indicator_e:", ":regional_indicator_symbol_e:", "🇪"),
    LETTER_F(":regional_indicator_f:", ":regional_indicator_symbol_f:", "🇫"),
    LETTER_G(":regional_indicator_g:", ":regional_indicator_symbol_g:", "🇬"),
    LETTER_H(":regional_indicator_h:", ":regional_indicator_symbol_h:", "🇭"),
    LETTER_I(":regional_indicator_i:", ":regional_indicator_symbol_i:", "🇮"),
    LETTER_J(":regional_indicator_j:", ":regional_indicator_symbol_j:", "🇯"),
    LETTER_K(":regional_indicator_k:", ":regional_indicator_symbol_k:", "🇰"),
    LETTER_L(":regional_indicator_l:", ":regional_indicator_symbol_l:", "🇱"),
    LETTER_M(":regional_indicator_m:", ":regional_indicator_symbol_m:", "🇲"),
    LETTER_N(":regional_indicator_n:", ":regional_indicator_symbol_n:", "🇳"),
    LETTER_O(":regional_indicator_o:", ":regional_indicator_symbol_o:", "🇴"),
    LETTER_P(":regional_indicator_p:", ":regional_indicator_symbol_p:", "🇵"),
    LETTER_Q(":regional_indicator_q:", ":regional_indicator_symbol_q:", "🇶"),
    LETTER_R(":regional_indicator_r:", ":regional_indicator_symbol_r:", "🇷"),
    LETTER_S(":regional_indicator_s:", ":regional_indicator_symbol_s:", "🇸"),
    LETTER_T(":regional_indicator_t:", ":regional_indicator_symbol_t:", "🇹"),
    LETTER_U(":regional_indicator_u:", ":regional_indicator_symbol_u:", "🇺"),
    LETTER_V(":regional_indicator_v:", ":regional_indicator_symbol_v:", "🇻"),
    LETTER_W(":regional_indicator_w:", ":regional_indicator_symbol_w:", "🇼"),
    LETTER_X(":regional_indicator_x:", ":regional_indicator_symbol_x:", "🇽"),
    LETTER_Y(":regional_indicator_y:", ":regional_indicator_symbol_y:", "🇾"),
    LETTER_Z(":regional_indicator_z:", ":regional_indicator_symbol_z:", "🇿");

    private static Map<String, Emoji> parseMap = setUpParseMap();
    private String discordAlias;
    // TODO Optimisation - get this from unicode2char
    private String unicodeFullString;
    private String unicode2char;


    Emoji(String discordAlias, String unicodeFullString) {
        this(discordAlias, null, unicodeFullString);
    }


    Emoji(String discordAlias, String emojiManagerAliasAlt, String unicodeFullString) {
        final String aliasSurround = ":";
        this.discordAlias = aliasSurround + discordAlias.replaceAll(aliasSurround, "") + aliasSurround;
        if (emojiManagerAliasAlt != null) {
            emojiManagerAliasAlt = emojiManagerAliasAlt.replaceAll(aliasSurround, "");
        }
        else {
            emojiManagerAliasAlt = this.discordAlias;
        }
        this.unicodeFullString = unicodeFullString;
        unicode2char = EmojiManager.getForAlias(emojiManagerAliasAlt).getUnicode();
        if (unicode2char == null) {
            throw new IllegalArgumentException("Could not find alias " + discordAlias + " in EmojiManager");
        }
    }


    public static Optional<Emoji> getFromString(String string) {
        if (parseMap.containsKey(string)) {
            return Optional.of(parseMap.get(string));
        }
        else {
            return Optional.empty();
        }
    }

    public static Optional<Emoji> getFromMessageReaction(MessageReaction messageReaction) {
        return getFromString(messageReaction.getReactionEmote().getName());
    }


    private static Map<String, Emoji> setUpParseMap() {
        Map<String, Emoji> map = new HashMap<>();
        for (Emoji emoji : Emoji.values()) {
            map.put(emoji.unicode2char, emoji);
        }
        return map;
    }


    public String getDiscordAlias() {
        return discordAlias;
    }


    public String getUnicode() {
        return unicodeFullString;
    }

    public void addAsReaction(Message message) {
        message.addReaction(unicodeFullString).queue();
    }


    public static List<Emoji> getLetters() {
        List<Emoji> emojiArguments = new ArrayList<>();
        emojiArguments.add(Emoji.LETTER_A);
        emojiArguments.add(Emoji.LETTER_B);
        emojiArguments.add(Emoji.LETTER_C);
        emojiArguments.add(Emoji.LETTER_D);
        emojiArguments.add(Emoji.LETTER_E);
        emojiArguments.add(Emoji.LETTER_F);
        emojiArguments.add(Emoji.LETTER_G);
        emojiArguments.add(Emoji.LETTER_H);
        emojiArguments.add(Emoji.LETTER_I);
        emojiArguments.add(Emoji.LETTER_J);
        emojiArguments.add(Emoji.LETTER_K);
        emojiArguments.add(Emoji.LETTER_L);
        emojiArguments.add(Emoji.LETTER_M);
        emojiArguments.add(Emoji.LETTER_N);
        emojiArguments.add(Emoji.LETTER_O);
        emojiArguments.add(Emoji.LETTER_P);
        emojiArguments.add(Emoji.LETTER_Q);
        emojiArguments.add(Emoji.LETTER_R);
        emojiArguments.add(Emoji.LETTER_S);
        emojiArguments.add(Emoji.LETTER_T);
        emojiArguments.add(Emoji.LETTER_U);
        emojiArguments.add(Emoji.LETTER_V);
        emojiArguments.add(Emoji.LETTER_W);
        emojiArguments.add(Emoji.LETTER_X);
        emojiArguments.add(Emoji.LETTER_Y);
        emojiArguments.add(Emoji.LETTER_Z);
        return emojiArguments;
    }
}
