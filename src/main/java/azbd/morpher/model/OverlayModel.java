package azbd.morpher.model;

import azbd.morpher.morph.MorphEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class OverlayModel extends GeoModel<MorphEntity> {

    private String overlayName;

    public OverlayModel() {
        this.overlayName = null;
    }

    public void setOverlayName(String name) {
        this.overlayName = (name != null && !name.isEmpty()) ? name : null;
    }

    public String getOverlayName() {
        return overlayName;
    }

    public boolean hasOverlay() {
        return overlayName != null;
    }

    @Override
    public Identifier getModelResource(MorphEntity entity) {
        if (overlayName == null) return null;
        return Identifier.of("morpher", "geo/" + overlayName + ".geo.json");
    }

    @Override
    public Identifier getTextureResource(MorphEntity entity) {
        if (overlayName == null) return null;
        return Identifier.of("morpher", "textures/" + overlayName + ".png");
    }

    @Override
    public Identifier getAnimationResource(MorphEntity entity) {
        return null;
    }

    public boolean modelExists(MorphEntity entity) {
        if (overlayName == null) return false;
        try {
            Identifier modelId = getModelResource(entity);
            return MinecraftClient.getInstance()
                    .getResourceManager()
                    .getResource(modelId)
                    .isPresent();
        } catch (Exception e) {
            return false;
        }
    }
}
