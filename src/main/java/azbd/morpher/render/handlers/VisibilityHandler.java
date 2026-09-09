package azbd.morpher.render.handlers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;

public class VisibilityHandler {

    private static final float INVISIBLE_SELF    = 0.15F;
    private static final float INVISIBLE_TEAM    = 0.15F;
    private static final float INVISIBLE_ENEMY   = 0.0F;
    private static final float VISIBLE           = 1.0F;

    public float calculateAlpha(PlayerEntity target, PlayerEntity viewer) {
        if (!target.isInvisible()) {
            return VISIBLE;
        }

        if (viewer != null && target.getUuid().equals(viewer.getUuid())) {
            return INVISIBLE_SELF;
        }

        if (viewer == null) {
            return INVISIBLE_ENEMY;
        }

        Team targetTeam = target.getScoreboardTeam();
        Team viewerTeam = viewer.getScoreboardTeam();

        if (targetTeam != null && viewerTeam != null && viewerTeam.isEqual(targetTeam)) {
            return INVISIBLE_TEAM;
        }

        return INVISIBLE_ENEMY;
    }

    public float getBodyAlpha(PlayerEntity target, PlayerEntity viewer) {
        return calculateAlpha(target, viewer);
    }

    public boolean shouldRenderArmor(PlayerEntity target) {
        if (!target.isInvisible()) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.player.getUuid().equals(target.getUuid());
    }

    public boolean shouldRenderItems(PlayerEntity target) {
        return true;
    }

    public boolean shouldRenderLayer(PlayerEntity target) {
        if (!target.isInvisible()) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.player.getUuid().equals(target.getUuid());
    }
}