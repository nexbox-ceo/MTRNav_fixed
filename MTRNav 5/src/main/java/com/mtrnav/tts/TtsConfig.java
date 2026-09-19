package com.mtrnav.tts;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persists the chosen voice across sessions. Nothing to configure beyond that -- OS-native voices need no API key or install. */
public final class TtsConfig {

	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mtrnav.properties");

	private TtsConfig() {
	}

	public static void load() {
		final Properties properties = new Properties();
		if (Files.exists(CONFIG_PATH)) {
			try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
				properties.load(in);
			} catch (IOException ignored) {
				// Fall through to defaults.
			}
		}
		final String voiceName = properties.getProperty("voice", TtsVoice.NONE.name());
		try {
			TtsEngine.currentVoice = TtsVoice.valueOf(voiceName);
		} catch (IllegalArgumentException e) {
			TtsEngine.currentVoice = TtsVoice.NONE;
		}
	}

	public static void save() {
		final Properties properties = new Properties();
		properties.setProperty("voice", TtsEngine.currentVoice.name());
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
				properties.store(out, "MTRNav settings");
			}
		} catch (IOException ignored) {
			// Not fatal -- the voice choice just won't persist to next launch.
		}
	}
}
