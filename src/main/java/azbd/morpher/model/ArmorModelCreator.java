package azbd.morpher.model;

import azbd.morpher.api.ArmorModelRegistry;
import azbd.morpher.morph.MorphEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class ArmorModelCreator extends GeoModel<MorphEntity> {
    private final ArmorModelRegistry.Entry entry;

    public ArmorModelCreator(ArmorModelRegistry.Entry entry) {
        this.entry = entry;
    }

    @Override
    public Identifier getModelResource(MorphEntity entity) {
        return entry.model();
    }

    @Override
    public Identifier getTextureResource(MorphEntity entity) {
        return entry.texture();
    }

    @Override
    public Identifier getAnimationResource(MorphEntity entity) {
        if (entry.animation() != null) {
            return entry.animation();
        }
        return Identifier.of("morpher", "animations/default.animation.json");
    }
}
