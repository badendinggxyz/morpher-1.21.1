package azbd.morpher.overlay;

import java.util.ArrayList;
import java.util.List;

public class CharacterOverlay {

    public String name      = "";
    public String model     = "";
    public String texture   = "";
    public String animation = "";
    public String emote_mode = "additive";
    public LayerDef layer = null;

    public static class LayerDef {
        public boolean enabled = true;
        public String model = "";
        public String texture = "";
        public List<String> textures = new ArrayList<>();
        public String getTexture(int index) {
            if (index <= 0 || texture == null) return texture != null ? texture : "";
            int listIdx = index - 1;
            if (textures == null || listIdx >= textures.size()) return texture;
            return textures.get(listIdx);
        }
        public int textureCount() {
            return 1 + (textures != null ? textures.size() : 0);
        }
    }
    public Equipment equipment = null;

    public static class Equipment {
        public String head  = "";
        public String chest = "";
        public String legs  = "";
        public String feet  = "";
    }
    public boolean isForcedEmoteMode() {
        return "forced".equalsIgnoreCase(emote_mode);
    }
    public String getDisplayName(String fallbackKey) {
        return (name != null && !name.isEmpty()) ? name : fallbackKey;
    }
}