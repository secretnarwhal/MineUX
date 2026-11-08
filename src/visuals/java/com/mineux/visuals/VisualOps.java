package com.mineux.visuals;

/**
 * What the visuals' mixins ask {@link VisualBridge}, one number a hook. The numbers are stable:
 * a host mod with hooks of its own may route those ops straight on to {@link VisualBridge}
 * by number. The few hooks added later have numbers of their own from 150 up.
 */
public final class VisualOps {

    private VisualOps() {}

    // ---- the client's frame and tick ----------------------------------------------------------

    /** {@code dispatch(OP_TICK, minecraft)}: the tail of every client tick. */
    public static final int OP_TICK = 23;
    /** {@code dispatch(OP_MOUSE_FRAME, minecraft)}: the head of each frame's mouse handling. */
    public static final int OP_MOUSE_FRAME = 28;
    /** {@code dispatch(OP_LIGHTMAP, lightmapRenderState)}: the frame's lightmap, worked out. */
    public static final int OP_LIGHTMAP = 30;
    /** {@code dispatch(OP_RENDER_GIZMOS, minecraft, partialTick)}: the head of the level's frame, for world gizmos. */
    public static final int OP_RENDER_GIZMOS = 34;
    /** {@code dispatch(OP_CURSOR_TRAIL, graphics, mouseX, mouseY)} over every screen; {@code dispatch(OP_CURSOR_TRAIL_CLOSED)} as one is removed. */
    public static final int OP_CURSOR_TRAIL = 35;
    public static final int OP_CURSOR_TRAIL_CLOSED = 21;

    // ---- the server's events, only read -------------------------------------------------------

    /** {@code dispatch(OP_ENTITY_EVENT, entityEventPacket)} on the client thread. */
    public static final int OP_ENTITY_EVENT = 44;
    /** {@code dispatch(OP_DAMAGE_EVENT, damageEventPacket)}. */
    public static final int OP_DAMAGE_EVENT = 46;
    /** {@code dispatch(OP_ATTACK, entity)}: your attack as you make it. */
    public static final int OP_ATTACK = 150;

    // ---- world and screen visuals (70-79) -----------------------------------------------------

    public static final int OP_WORLD_FOG = 70;
    public static final int OP_WORLD_SKY = 71;
    public static final int OP_WORLD_CLOCK = 72;
    /** {@code query(OP_WORLD_HIDE, code)}, a {@code HIDE_} code below: true leaves that piece undrawn. */
    public static final int OP_WORLD_HIDE = 73;
    /** {@code query(OP_WORLD_HIDE_AT, thing)}: an explosion's centre, or a sound about to play. */
    public static final int OP_WORLD_HIDE_AT = 74;
    public static final int OP_WORLD_ITEM = 75;
    public static final int OP_WORLD_ITEM_DRAW = 76;
    public static final int OP_WORLD_FRAME = 78;
    public static final int OP_WORLD_FRAME_END = 79;

    /** What {@link #OP_WORLD_HIDE} asks about. Not op numbers: a code inside that one op. */
    public static final int HIDE_SCOREBOARD = 0;
    public static final int HIDE_NAUSEA = 1;
    public static final int HIDE_PORTAL = 2;
    public static final int HIDE_SPYGLASS = 3;
    public static final int HIDE_VIGNETTE = 4;
    public static final int HIDE_TOTEM_ANIMATION = 5;
    public static final int HIDE_FIRE = 6;
    public static final int HIDE_TOTEM_PARTICLES = 7;
    public static final int HIDE_CLOUDS = 8;
    public static final int HIDE_SKY_DISC = 9;
    public static final int HIDE_END_SKY = 10;
    public static final int HIDE_SKY_EXTRAS = 11;
    public static final int HIDE_SUN_MOON = 12;
    public static final int HIDE_EXPLOSIONS = 13;

    // ---- HUD skins (80-89) --------------------------------------------------------------------

    public static final int OP_HUD_CROSSHAIR = 80;
    public static final int OP_HUD_HOTBAR = 81;
    public static final int OP_HUD_STATUS = 82;
    public static final int OP_HUD_EXPERIENCE = 83;
    public static final int OP_HUD_SCOREBOARD = 84;
    public static final int OP_CHAT_ADD = 85;
    public static final int OP_CHAT_HISTORY = 86;
    public static final int OP_CHAT_DRAW = 87;
    public static final int OP_CHAT_LINE = 88;
    public static final int OP_THEME_TEXTURE = 89;

    // ---- your own player (90-99) --------------------------------------------------------------

    public static final int OP_SELF_ENTITIES_EXTRACTED = 90;
    public static final int OP_SELF_AVATAR_EXTRACTED = 91;
    public static final int OP_SELF_AVATAR_SUBMIT = 92;
    public static final int OP_SELF_HUMANOID_POSE = 93;
    public static final int OP_SELF_SKIN = 94;
    public static final int OP_SELF_ARMOR_LAYER = 95;
    public static final int OP_SELF_ARMOR_TEXTURE = 96;
    public static final int OP_SELF_ARMOR_TYPE = 97;
    public static final int OP_SELF_ARMOR_TINT = 98;

    // ---- the first-person hand (100-109) ------------------------------------------------------

    public static final int OP_HAND_ATTACK = 100;
    public static final int OP_HAND_BEGIN = 101;
    public static final int OP_HAND_EQUIP = 102;
    public static final int OP_HAND_SWING = 103;
    public static final int OP_HAND_ITEM = 104;
    public static final int OP_HAND_DRAWN = 105;

    // ---- the camera (110-119) -----------------------------------------------------------------

    public static final int OP_CAMERA_LOOK = 110;
    public static final int OP_CAMERA_YAW = 111;
    public static final int OP_CAMERA_PITCH = 112;
    public static final int OP_CAMERA_DISTANCE = 113;
    public static final int OP_CAMERA_ALIGNED = 115;
    public static final int OP_NO_HURT_CAM = 116;
    public static final int OP_NO_VIEW_BOB = 117;
    public static final int OP_ASPECT = 118;
    public static final int OP_MOTION_BLUR = 119;

    // ---- cosmetics, zoom, hit effects ---------------------------------------------------------

    public static final int OP_COSMETIC_CAPE = 120;
    public static final int OP_ZOOM_FOV = 130;
    public static final int OP_OPTIONS_SAVE = 131;
    public static final int OP_FX_CRIT_PACKET = 140;
    public static final int OP_FX_CRIT_OWN = 141;
}
