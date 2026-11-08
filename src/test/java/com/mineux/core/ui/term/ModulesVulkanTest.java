
package com.mineux.core.ui.term;

import com.mineux.core.gpu.Backend;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code vulkan=no} in modules.txt: on Vulkan the line is left out of the tree, a dir with all under it. */
class ModulesVulkanTest {

    private static final String TEXT = String.join("\n",
        "dir looks cfg=none | things to see",
        "set plain plainField | runs anywhere",
        "set glonly glField vulkan=no | needs OpenGL",
        "set talky talkField | mentions vulkan=no only in its description",
        "dir looks/glow cfg=none toggle=glow vulkan=no | an OpenGL-only effect",
        "set strength glowStrength | under the dropped dir",
        "dir looks/glow/inner cfg=none | under it too",
        "set depth innerDepth | and here",
        "dir looks/after cfg=none | back to normal",
        "set kept keptField | still here",
        "");

    @Test
    void openGlShowsEverything() {
        Node root = Modules.build(TEXT, false);
        Node looks = root.child("looks");
        assertNotNull(looks.child("glow"));
        assertNotNull(looks.child("glow").child("inner"));
        assertEquals(3, looks.settings.size());
    }

    @Test
    void vulkanLeavesOutWhatNeedsOpenGl() {
        Node root = Modules.build(TEXT, true);
        Node looks = root.child("looks");
        assertNull(looks.child("glow"));
        List<String> keys = new ArrayList<>();
        for (Setting s : looks.settings) keys.add(s.key);
        assertEquals(List.of("plain", "talky"), keys);
        Node after = looks.child("after");
        assertNotNull(after);
        assertEquals(1, after.settings.size());
        List<Node> all = new ArrayList<>();
        root.walk(all);
        for (Node n : all) assertFalse(n.name.equals("inner") || n.name.equals("glow"), n.name);
    }

    @Test
    void countsOnlyMarkedLines() {
        assertEquals(2, Modules.openGlOnly(TEXT));
        assertTrue(Modules.needsOpenGl("set x y vulkan=no | z"));
        assertFalse(Modules.needsOpenGl("set x y | needs vulkan=no to run"));
    }

    @Test
    void theRealFileBuildsTheSameOnBothWhileNothingIsMarked() throws IOException {
        String text;
        try (InputStream in = Modules.class.getResourceAsStream("/mineux/modules.txt")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<Node> gl = new ArrayList<>();
        Modules.build(text, false).walk(gl);
        List<Node> vk = new ArrayList<>();
        Modules.build(text, true).walk(vk);
        int marked = Modules.openGlOnly(text);
        if (marked == 0) assertEquals(gl.size(), vk.size());
        else assertTrue(vk.size() <= gl.size());
        // No device in a test: the game is never taken for Vulkan, and nothing is said.
        assertFalse(Backend.vulkan());
        assertEquals(0, Modules.vulkanHidden());
        assertNull(Modules.vulkanNote());
    }
}
