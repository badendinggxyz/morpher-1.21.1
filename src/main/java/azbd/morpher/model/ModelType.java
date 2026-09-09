package azbd.morpher.model;

public enum ModelType {
    ARMOR("armor"),
    ELYTRA("elytra"),
    LAYER("layer"),
    OVERLAY("overlay");

    private final String id;

    ModelType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static ModelType fromId(String id) {
        for (ModelType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    public boolean isArmor() {
        return this == ARMOR;
    }

    public boolean isElytra() {
        return this == ELYTRA;
    }

    public boolean isLayer() {
        return this == LAYER;
    }

    public boolean isOverlay() {
        return this == OVERLAY;
    }
}