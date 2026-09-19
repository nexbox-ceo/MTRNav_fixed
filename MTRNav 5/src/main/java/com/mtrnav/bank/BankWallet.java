package com.mtrnav.bank;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Server-authoritative wallet balances, keyed by player UUID. Deliberately
 * simple: a properties file under the Fabric config dir rather than a
 * proper {@code PersistentState} tied to the world save -- this build had
 * no real Minecraft jar to verify PersistentState's exact 1.18.2 API
 * against, and a flat file is something this build COULD verify by just
 * reading/writing it directly. The real tradeoff: balances are per-player
 * globally, not per-world/per-server. Fine for a personal or small-server
 * mod; swap in a proper PersistentState if you need per-world wallets.
 */
public final class BankWallet {

	private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("mtrnav-wallets.properties");
	private static final ConcurrentMap<UUID, Long> BALANCES = new ConcurrentHashMap<>();
	private static volatile boolean loaded;

	private BankWallet() {
	}

	public static long getBalance(UUID player) {
		ensureLoaded();
		return BALANCES.getOrDefault(player, 0L);
	}

	/** Adds (or, with a negative amount, removes -- never below zero) and persists immediately. */
	public static long adjustBalance(UUID player, long delta) {
		ensureLoaded();
		final long newBalance = Math.max(0, getBalance(player) + delta);
		BALANCES.put(player, newBalance);
		save();
		return newBalance;
	}

	private static synchronized void ensureLoaded() {
		if (loaded) {
			return;
		}
		final Properties properties = new Properties();
		if (Files.exists(FILE_PATH)) {
			try (InputStream in = Files.newInputStream(FILE_PATH)) {
				properties.load(in);
			} catch (IOException ignored) {
				// Start empty on a corrupt/unreadable file rather than failing to load the server.
			}
		}
		properties.forEach((key, value) -> {
			try {
				BALANCES.put(UUID.fromString((String) key), Long.parseLong((String) value));
			} catch (IllegalArgumentException ignored) {
				// Skip a malformed line rather than aborting the whole load.
			}
		});
		loaded = true;
	}

	private static synchronized void save() {
		final Properties properties = new Properties();
		BALANCES.forEach((uuid, balance) -> properties.setProperty(uuid.toString(), Long.toString(balance)));
		try {
			Files.createDirectories(FILE_PATH.getParent());
			try (OutputStream out = Files.newOutputStream(FILE_PATH)) {
				properties.store(out, "MTRNav Bank wallet balances (emeralds), keyed by player UUID");
			}
		} catch (IOException ignored) {
			// Not fatal -- worst case a "Keep" doesn't persist across a server restart.
		}
	}
}
