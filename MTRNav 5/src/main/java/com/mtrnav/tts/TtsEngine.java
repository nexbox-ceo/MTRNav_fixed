package com.mtrnav.tts;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Speaks announcements through whatever speech engine is already built into
 * the player's OS -- no account, no API key, no extra program to install.
 * (This mod has been through two other approaches: espeak-ng, which needed
 * installing and PATH setup that turned out to be more friction than it was
 * worth, and ElevenLabs, which needed an account/API key/internet. This is
 * the "just use what's already on the computer" version.)
 *
 * <ul>
 *   <li><b>Windows</b>: PowerShell + {@code System.Speech.Synthesis}, which
 *       ships with every Windows install. Rather than hard-coding a voice
 *       name like "Microsoft Hazel Desktop" (which depends on which
 *       language packs happen to be installed), it enumerates the
 *       installed voices at speak-time and picks one matching the desired
 *       culture ("en-GB"/"en-US") and gender, falling back to whatever
 *       voice is installed if no exact match exists.</li>
 *   <li><b>macOS</b>: the {@code say} command with Apple's long-standing
 *       stock voices (Daniel/Kate for UK, Alex/Samantha for US). If the
 *       specific voice isn't present, falls back to the system default
 *       voice rather than staying silent.</li>
 *   <li><b>Linux</b>: honestly the weak link here -- unlike Windows/macOS,
 *       desktop Linux has no single universal built-in TTS engine. This
 *       tries {@code spd-say} (speech-dispatcher, often present on
 *       GNOME/KDE desktops with accessibility features on) and falls back
 *       to {@code espeak-ng}/{@code espeak} if installed, but on a fresh
 *       Linux install none of these may exist -- if so, this silently does
 *       nothing rather than erroring.</li>
 * </ul>
 *
 * None of these three code paths could be run and listened to in the
 * environment this was written in -- if a voice stays silent, that's the
 * real first-run verification step, not a sign something is definitely
 * broken.
 */
public final class TtsEngine {

	public static volatile TtsVoice currentVoice = TtsVoice.NONE;

	private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(r -> {
		final Thread thread = new Thread(r, "mtrnav-tts");
		thread.setDaemon(true);
		return thread;
	});

	private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

	private TtsEngine() {
	}

	/** Queues an announcement. Never blocks the caller, never throws. */
	public static void speak(String text) {
		final TtsVoice voice = currentVoice;
		if (voice == TtsVoice.NONE || text == null || text.isBlank()) {
			return;
		}
		QUEUE.submit(() -> {
			try {
				if (OS_NAME.contains("win")) {
					speakWindows(text, voice);
				} else if (OS_NAME.contains("mac")) {
					speakMac(text, voice);
				} else {
					speakLinuxBestEffort(text, voice);
				}
			} catch (Exception ignored) {
				// Whatever OS TTS mechanism failed -- skip this one announcement.
			}
		});
	}

	private static void speakWindows(String text, TtsVoice voice) throws Exception {
		final String escapedText = text.replace("'", "''");
		final String script = "Add-Type -AssemblyName System.Speech; "
				+ "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
				+ "$match = $synth.GetInstalledVoices() | Where-Object { "
				+ "$_.VoiceInfo.Culture.Name -like '" + voice.windowsCulture + "*' -and $_.VoiceInfo.Gender -eq '" + voice.windowsGender + "' "
				+ "} | Select-Object -First 1; "
				+ "if ($match) { $synth.SelectVoice($match.VoiceInfo.Name) } "
				+ "$synth.Speak('" + escapedText + "');";
		// -EncodedCommand sidesteps quoting/escaping entirely between Java, the
		// shell, and PowerShell -- it wants UTF-16LE bytes, Base64-encoded.
		final String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
		runAndWait("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
	}

	private static void speakMac(String text, TtsVoice voice) throws Exception {
		final int exitCode = runAndWait("say", "-v", voice.macVoiceName, text);
		if (exitCode != 0) {
			// The named voice might not be installed on this Mac -- fall back
			// to the system default voice rather than staying silent.
			runAndWait("say", text);
		}
	}

	private static void speakLinuxBestEffort(String text, TtsVoice voice) {
		// No single command is guaranteed present, so try a short chain and
		// stop at whichever one actually exists.
		if (tryRun("spd-say", text)) {
			return;
		}
		final String espeakVariant = (voice.windowsCulture == null ? "en-us" : voice.windowsCulture.toLowerCase(Locale.ROOT))
				+ ("Male".equals(voice.windowsGender) ? "+m3" : "+f3");
		if (tryRun("espeak-ng", "-v", espeakVariant, text)) {
			return;
		}
		tryRun("espeak", "-v", espeakVariant, text);
	}

	/** @return true if the command was found and ran (regardless of its exit code) -- false only if the binary itself doesn't exist. */
	private static boolean tryRun(String... command) {
		try {
			runAndWait(command);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static int runAndWait(String... command) throws Exception {
		final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		process.waitFor(15, TimeUnit.SECONDS);
		return process.exitValue();
	}
}
