package com.mtrnav.phone;

import com.mtrnav.MTRNavClient;
import com.mtrnav.compat.MTRDataBridge;
import com.mtrnav.journey.JourneyManager;
import com.mtrnav.routing.RouteLeg;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;

/**
 * A simple orthographic, north-up (not player-heading-up, to avoid needing
 * a rotated-quad renderer -- see {@link com.mtrnav.client.JourneyHudOverlay}
 * for the same tradeoff) top-down view of nearby platforms, centered on the
 * player.
 */
public final class MiniMapWidget {

	private static final double VIEW_RADIUS_BLOCKS = 150;

	public void render(MatrixStack matrices, int x, int y, int width, int height) {
		final MinecraftClient client = MinecraftClient.getInstance();
		GuiDraw.fill(matrices, x, y, x + width, y + height, 0xFF0C1A0C);
		if (client.player == null || !MTRDataBridge.isDataAvailable()) {
			return;
		}

		final BlockPos playerPos = client.player.getBlockPos();
		final int centerX = x + width / 2;
		final int centerY = y + height / 2;
		final double scale = (Math.min(width, height) / 2.0) / VIEW_RADIUS_BLOCKS;

		final JourneyManager journey = MTRNavClient.JOURNEY;
		final RouteLeg activeLeg = journey.isActive() ? journey.currentLeg() : null;

		for (final Platform platform : MTRDataBridge.getPlatforms()) {
			final Position pos = platform.getMidPosition();
			final double dx = pos.getX() - playerPos.getX();
			final double dz = pos.getZ() - playerPos.getZ();
			if (dx * dx + dz * dz > VIEW_RADIUS_BLOCKS * VIEW_RADIUS_BLOCKS) {
				continue;
			}
			final int px = (int) (centerX + dx * scale);
			final int py = (int) (centerY + dz * scale);
			if (px < x || px >= x + width || py < y || py >= y + height) {
				continue;
			}
			final boolean onActiveLeg = activeLeg != null && (samePlatform(platform, activeLeg.boardPlatform) || samePlatform(platform, activeLeg.alightPlatform));
			final int color = onActiveLeg ? 0xFFFFFF33 : 0xFF66AA66;
			GuiDraw.fill(matrices, px - 1, py - 1, px + 2, py + 2, color);
		}

		// Player blip, always dead center.
		GuiDraw.fill(matrices, centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xFFFFFFFF);
	}

	private static boolean samePlatform(Platform a, Platform b) {
		return a != null && b != null && a.getId() == b.getId();
	}
}
