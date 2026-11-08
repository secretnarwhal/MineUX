package com.mineux.core.ui.term;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which mod a menu button belongs to, and the icon to draw beside it: the mod's own icon (its
 * {@code fabric.mod.json} one, uploaded once), or failing that the sprite an icon button
 * draws. Lets a button another mod added be listed under that mod's name, and named after it
 * when its own label says little.
 * <p>
 * A button is traced to its mod by the classes behind it, looked up in every mod's jar: the
 * button's own class when a mod subclassed one, then the class that wrote what it does when
 * pressed (the lambda handed to {@code Button.builder}), then a translation key of the mod's
 * own ({@code modmenu.title}). Anything traced to Minecraft is vanilla's.
 */
final class ButtonMods {

    /** Drawn size, in GUI px, as a server's icon is. */
    static final int SIZE = ServerIcons.SIZE;

    /** A mod: its id, its display name, and its icon file inside the jar (null for none). */
    record Mod(String id, String name, String iconPath) {}

    private static final Map<String, Optional<Mod>> BY_CLASS = new HashMap<>();
    private static final Map<String, Identifier> ICONS = new HashMap<>();
    private static Field onPress, sprite;
    /** Looked up once each, found or not. */
    private static boolean onPressLooked, spriteLooked;

    private ButtonMods() {}

    /** The mod that added this button, or null for one of Minecraft's (or one past tracing). */
    static Mod of(AbstractButton button) {
        Mod mod = byClass(button.getClass().getName());
        if (mod != null) return mod;

        if (Button.class.isInstance(button)) {
            Object press = read(onPressField(), button);
            if (press != null) mod = byClass(press.getClass().getName());
            if (mod != null) return mod;
        }

        Component message = button.getMessage();
        if (message != null && TranslatableContents.class.isInstance(message.getContents())) {
            String key = ((TranslatableContents) message.getContents()).getKey();
            int dot = key.indexOf('.');
            if (dot > 0) {
                Optional<ModContainer> owner = FabricLoader.getInstance().getModContainer(key.substring(0, dot));
                if (owner.isPresent() && !vanilla(owner.get().getMetadata().getId())) return mod(top(owner.get()));
            }
        }
        return null;
    }

    /** The mod whose jar holds that class; lambdas are looked up by the class that wrote them. */
    private static Mod byClass(String name) {
        int lambda = name.indexOf("$$Lambda");
        if (lambda > 0) name = name.substring(0, lambda);
        if (name.startsWith("net.minecraft.") || name.startsWith("com.mojang.") || name.startsWith("java.")
            || name.startsWith("jdk.") || name.startsWith("com.mineux.")) {
            return null;
        }

        Optional<Mod> known = BY_CLASS.get(name);
        if (known != null) return known.orElse(null);

        String resource = name.replace('.', '/') + ".class";
        Mod found = null;
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if (vanilla(container.getMetadata().getId())) continue;
            if (container.findPath(resource).isPresent()) {
                found = mod(top(container));
                break;
            }
        }
        BY_CLASS.put(name, Optional.ofNullable(found));
        return found;
    }

    private static boolean vanilla(String id) {
        return id.equals("minecraft") || id.equals("java") || id.equals("fabricloader");
    }

    /** A library bundled inside a mod is listed as the mod that bundles it. */
    private static ModContainer top(ModContainer container) {
        for (int depth = 0; depth < 8 && container.getContainingMod().isPresent(); depth++) {
            container = container.getContainingMod().get();
        }
        return container;
    }

    private static Mod mod(ModContainer container) {
        String id = container.getMetadata().getId();
        String name = container.getMetadata().getName();
        return new Mod(id, name == null || name.isBlank() ? id : name, container.getMetadata().getIconPath(32).orElse(null));
    }

    // ---- icons ------------------------------------------------------------

    /** Whether the button has an icon to draw. */
    static boolean hasIcon(MenuButtons.Entry entry) {
        return (entry.mod() != null && entry.mod().iconPath() != null) || spriteOf(entry.button()) != null;
    }

    /** Draws the button's icon at {@code x, y}; nothing for a button without one. */
    static void draw(GuiGraphicsExtractor g, MenuButtons.Entry entry, int x, int y) {
        Identifier texture = entry.mod() == null ? null : texture(entry.mod());
        if (texture != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
            return;
        }
        Identifier icon = spriteOf(entry.button());
        if (icon != null) g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x, y, SIZE, SIZE);
    }

    /** The mod's icon, uploaded the first time it is drawn; null when it has none or it would not load. */
    private static Identifier texture(Mod mod) {
        if (mod.iconPath() == null) return null;
        if (ICONS.containsKey(mod.id())) return ICONS.get(mod.id());

        Identifier id = null;
        try {
            Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(mod.id());
            Optional<Path> file = container.isPresent() ? container.get().findPath(mod.iconPath()) : Optional.empty();
            if (file.isPresent()) {
                NativeImage image = NativeImage.read(Files.readAllBytes(file.get()));
                String path = "modicon/" + mod.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
                id = Identifier.fromNamespaceAndPath("mineux", path);
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "mineux mod icon " + mod.id(), image));
            }
        } catch (Exception e) {
            id = null;
        }
        ICONS.put(mod.id(), id);
        return id;
    }

    /** The sprite an icon button draws on its face, or null for a button that is only text. */
    private static Identifier spriteOf(AbstractButton button) {
        if (!SpriteIconButton.class.isInstance(button)) return null;
        Object sprites = read(spriteField(), button);
        return WidgetSprites.class.isInstance(sprites) ? ((WidgetSprites) sprites).enabled() : null;
    }

    // ---- reflection -------------------------------------------------------

    private static Field onPressField() {
        if (!onPressLooked) {
            onPressLooked = true;
            onPress = field(Button.class, "onPress");
        }
        return onPress;
    }

    private static Field spriteField() {
        if (!spriteLooked) {
            spriteLooked = true;
            sprite = field(SpriteIconButton.class, "sprite");
        }
        return sprite;
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object read(Field field, Object target) {
        if (field == null) return null;
        try {
            return field.get(target);
        } catch (Exception e) {
            return null;
        }
    }
}
