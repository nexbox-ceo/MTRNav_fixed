package com.mtrnav.util;

import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Position;

/**
 * Yaw/bearing math for the turn-by-turn HUD pointer.
 *
 * Minecraft's yaw convention: 0 = south (+Z), 90 = west (-X), 180 = north
 * (-Z), 270/-90 = east (+X), increasing clockwise when viewed from above.
 * atan2 gives a standard mathematical angle, so we convert once here and
 * keep every other call site oblivious to the conversion.
 */
public final class AngleUtil {

	private AngleUtil() {
	}

	/** Absolute yaw (Minecraft convention, degrees) from {@code from} to {@code to}. */
	public static float yawTo(double fromX, double fromZ, double toX, double toZ) {
		final double dx = toX - fromX;
		final double dz = toZ - fromZ;
		// atan2(-dx, dz) is the standard Minecraft "yaw towards a point" formula.
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		return (float) wrapDegrees(yaw);
	}

	public static float yawTo(BlockPos from, Position to) {
		return yawTo(from.getX() + 0.5, from.getZ() + 0.5, to.getX() + 0.5, to.getZ() + 0.5);
	}

	/**
	 * Signed angle the player needs to turn (degrees, -180..180) to face the
	 * checkpoint: positive = turn right, negative = turn left. This is what
	 * drives the HUD direction pointer's rotation.
	 */
	public static float relativeBearing(float playerYaw, float targetYaw) {
		return (float) wrapDegrees(targetYaw - playerYaw);
	}

	private static double wrapDegrees(double degrees) {
		double wrapped = degrees % 360.0;
		if (wrapped >= 180.0) {
			wrapped -= 360.0;
		} else if (wrapped < -180.0) {
			wrapped += 360.0;
		}
		return wrapped;
	}
}
