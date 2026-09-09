package azbd.morpher.render.layer;

import azbd.morpher.assets.AssetServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

import java.nio.file.Files;
import java.nio.file.Path;

public class AnimatedLayerModel extends GeoModel<AnimatedLayerAnimatable> {

    private String layerName = "empty";

    public void setLayerName(String name) {
        this.layerName = (name == null || name.isEmpty()) ? "empty" : name;
    }

    @Override
    public Identifier getModelResource(AnimatedLayerAnimatable animatable) {
        Identifier id = Identifier.of("morpher", "geo/" + layerName + ".geo.json");
        return exists(id) ? id : Identifier.of("morpher", "geo/player_wide.geo.json");
    }

    @Override
    public Identifier getTextureResource(AnimatedLayerAnimatable animatable) {
        return Identifier.of("morpher", "textures/" + layerName + ".png");
    }

    @Override
    public Identifier getAnimationResource(AnimatedLayerAnimatable animatable) {
        Identifier id = Identifier.of("morpher", "animations/" + layerName + ".animation.json");
        return exists(id) ? id : Identifier.of("morpher", "animations/default.animation.json");
    }

    private boolean exists(Identifier id) {
        try {
            Path path = AssetServer.getDataPath().resolve(id.getPath());
            if (Files.exists(path)) return true;
            return MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent();
        } catch (Exception e) {
            return false;
        }
    }
}
