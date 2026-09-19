package com.mtrnav.tts;

/**
 * The five voice options shown in the phone's Settings app, mapped to
 * whatever each OS's own built-in speech engine calls that accent/gender --
 * see {@link TtsEngine} for how each platform is handled.
 */
public enum TtsVoice {
	NONE(null, null, null, null),
	UK_MALE("UK Male", "en-GB", "Male", "Daniel"),
	UK_FEMALE("UK Female", "en-GB", "Female", "Kate"),
	US_MALE("US Male", "en-US", "Male", "Alex"),
	US_FEMALE("US Female", "en-US", "Female", "Samantha");

	public final String label;
	/** Windows: matched against SAPI voices' Culture.Name (e.g. "en-GB"). */
	public final String windowsCulture;
	/** Windows: matched against SAPI voices' Gender ("Male"/"Female"). */
	public final String windowsGender;
	/** macOS: the `say -v <name>` voice name. These four are long-standing stock macOS voices, but Apple can still remove/rename them in future OS versions. */
	public final String macVoiceName;

	TtsVoice(String label, String windowsCulture, String windowsGender, String macVoiceName) {
		this.label = label == null ? "None" : label;
		this.windowsCulture = windowsCulture;
		this.windowsGender = windowsGender;
		this.macVoiceName = macVoiceName;
	}
}
