package com.mtrnav.util;

import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Position;

/**
 * Pure 3D Euclidean-distance helpers. Kept separate from anything MTR- or
 * Minecraft-specific so it's trivial to unit test and reuse from both the
 * routing graph (which works in MTR's {@link Position}) and the phone UI
 * (which works in vanilla {@link BlockPos}).
 */
public final class DistanceUtil {

	private DistanceUtil() {
	}

	public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
		final double dx = x1 - x2;
		final double dy = y1 - y2;
		final double dz = z1 - z2;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public static double distance(BlockPos a, BlockPos b) {
		return distance(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
	}

	public static double distance(BlockPos player, Position mtrPosition) {
		return distance(player.getX(), player.getY(), player.getZ(), mtrPosition.getX(), mtrPosition.getY(), mtrPosition.getZ());
	}

	public static double distance(Position a, Position b) {
		return distance(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
	}

	/**
	 * Flat (Y-ignoring) distance -- useful for the mini-map and for the
	 * "close enough, treat platform as reached" checks, where a train
	 * platform one block up or down shouldn't matter.
	 */
	public static double distanceFlat(BlockPos player, Position mtrPosition) {
		final double dx = player.getX() - mtrPosition.getX();
		final double dz = player.getZ() - mtrPosition.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
