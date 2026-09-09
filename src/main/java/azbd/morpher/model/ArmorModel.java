package azbd.morpher.model;

import azbd.morpher.morph.MorphEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class ArmorModel extends GeoModel<MorphEntity> {

    private final ModelType type;
    private final EquipmentSlot slot;
    private String layerName         = "empty";
    private String customMaterialName = null;

    public ArmorModel(EquipmentSlot slot) {
        this.type = ModelType.ARMOR;
        this.slot = slot;
    }

    public static ArmorModel createElytra() { return new ArmorModel(ModelType.ELYTRA); }
    public static ArmorModel createLayer()  { return new ArmorModel(ModelType.LAYER);  }

    private ArmorModel(ModelType type) {
        this.type = type;
        this.slot = null;
    }

    public void setLayerName(String name) {
        if (type.isLayer()) this.layerName = name;
    }

    public String getLayerName() { return layerName; }

    public void setCustomMaterial(String materialName) {
        if (type == ModelType.ARMOR) this.customMaterialName = materialName;
    }

    public ModelType getType() { return type; }

    @Override
    public Identifier getModelResource(MorphEntity entity) {
        return switch (type) {
            case ARMOR  -> getArmorModel();
            case ELYTRA -> Identifier.of("morpher", "geo/elytra.geo.json");
            case LAYER  -> Identifier.of("morpher", "geo/" + layerName + ".geo.json");
            case OVERLAY -> null;
        };
    }

    @Override
    public Identifier getTextureResource(MorphEntity entity) {
        return switch (type) {
            case ARMOR  -> getArmorTexture(entity);
            case ELYTRA -> getElytraTexture(entity);
            case LAYER  -> Identifier.of("morpher", "textures/" + layerName + ".png");
            case OVERLAY -> null;
        };
    }

    @Override
    public Identifier getAnimationResource(MorphEntity entity) {
        return Identifier.of("morpher", "animations/default.animation.json");
    }

    private Identifier getArmorModel() {
        if (slot == null) return null;
        return switch (slot) {
            case HEAD  -> Identifier.of("morpher", "geo/head.geo.json");
            case CHEST -> Identifier.of("morpher", "geo/chest.geo.json");
            case LEGS  -> Identifier.of("morpher", "geo/legs.geo.json");
            case FEET  -> Identifier.of("morpher", "geo/feet.geo.json");
            default    -> null;
        };
    }

    private Identifier getArmorTexture(MorphEntity entity) {
        if (slot == null) return null;

        if (customMaterialName != null && !customMaterialName.isEmpty()) {
            String layer = (slot == EquipmentSlot.LEGS) ? "layer_2" : "layer_1";
            return Identifier.of("minecraft",
                    "textures/models/armor/" + customMaterialName + "_" + layer + ".png");
        }

        if (!(entity.getCurrentEntity() instanceof PlayerEntity)) return null;
        PlayerEntity player = (PlayerEntity) entity.getCurrentEntity();

        ItemStack armorStack = player.getEquippedStack(slot);
        Item item = armorStack.getItem();

        if (item == Items.TURTLE_HELMET) {
            return Identifier.of("minecraft", "textures/models/armor/turtle_layer_1.png");
        }

        if (!(item instanceof ArmorItem)) return null;
        ArmorItem armorItem = (ArmorItem) item;

        String materialName = getMaterialName(armorItem);
        if (materialName != null) {
            String layer = (slot == EquipmentSlot.LEGS) ? "layer_2" : "layer_1";
            return Identifier.of("minecraft",
                    "textures/models/armor/" + materialName + "_" + layer + ".png");
        }

        return resolveModdedArmorTexture(armorItem);
    }

    private String getMaterialName(ArmorItem armorItem) {
        String key = armorItem.getMaterial().value().equipSound().value().getId().getPath();
        if (key.contains("leather"))   return "leather";
        if (key.contains("chain"))     return "chainmail";
        if (key.contains("iron"))      return "iron";
        if (key.contains("gold"))      return "gold";
        if (key.contains("diamond"))   return "diamond";
        if (key.contains("netherite")) return "netherite";
        return null;
    }

    private Identifier resolveModdedArmorTexture(ArmorItem armorItem) {
        if (slot == null) return null;

        try {
            Object material = armorItem.getMaterial().value();
            RegistryKey<?> equipKey = null;

            for (java.lang.reflect.Method m : material.getClass().getMethods()) {
                if (m.getParameterCount() == 0
                        && RegistryKey.class.isAssignableFrom(m.getReturnType())) {
                    equipKey = (RegistryKey<?>) m.invoke(material);
                    break;
                }
            }

            if (equipKey != null) {

                Identifier assetId = equipKey.getValue();
                String layerFolder = (slot == EquipmentSlot.LEGS) ? "humanoid_leggings" : "humanoid";
                Identifier candidate = Identifier.of(assetId.getNamespace(),
                        "textures/entity/equipment/" + layerFolder + "/" + assetId.getPath() + ".png");
                if (textureExists(candidate)) return candidate;
            }
        } catch (Throwable ignored) {

        }

        try {
            Identifier soundId = armorItem.getMaterial().value().equipSound().value().getId();

            String soundPath = soundId.getPath();

            int lastUnderscore = soundPath.lastIndexOf('_');
            if (lastUnderscore >= 0) {
                String guessedName = soundPath.substring(lastUnderscore + 1);
                String layerSuffix = (slot == EquipmentSlot.LEGS) ? "_layer_2" : "_layer_1";

                Identifier candidate = Identifier.of(soundId.getNamespace(),
                        "textures/models/armor/" + guessedName + layerSuffix + ".png");
                if (textureExists(candidate)) return candidate;

                candidate = Identifier.of("minecraft",
                        "textures/models/armor/" + guessedName + layerSuffix + ".png");
                if (textureExists(candidate)) return candidate;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static boolean textureExists(Identifier id) {
        try {
            return MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    private Identifier getElytraTexture(MorphEntity entity) {
        if (!(entity.getCurrentEntity() instanceof PlayerEntity)) {
            return Identifier.of("minecraft", "textures/entity/elytra.png");
        }
        PlayerEntity player = (PlayerEntity) entity.getCurrentEntity();

        ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);

        NbtComponent customData = chestStack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null && customData.contains("ElytraTexture")) {
            String customTexture = customData.copyNbt().getString("ElytraTexture");
            if (!customTexture.isEmpty()) {
                return Identifier.tryParse(customTexture);
            }
        }

        return Identifier.of("minecraft", "textures/entity/elytra.png");
    }

    public boolean isType(ModelType checkType) { return this.type == checkType; }
    public EquipmentSlot getSlot()             { return slot; }

    public boolean isLayerEmpty() {
        return type.isLayer() && (layerName == null || layerName.isEmpty()
                || layerName.equals("empty"));
    }

    public void resetLayer() {
        if (type.isLayer()) this.layerName = "empty";
    }

    @Override
    public String toString() {
        return switch (type) {
            case ARMOR  -> String.format("ArmorModel{type=ARMOR, slot=%s}", slot);
            case ELYTRA -> "ArmorModel{type=ELYTRA}";
            case LAYER  -> String.format("ArmorModel{type=LAYER, layerName='%s'}", layerName);
            case OVERLAY -> "ArmorModel{type=OVERLAY}";
        };
    }
}
