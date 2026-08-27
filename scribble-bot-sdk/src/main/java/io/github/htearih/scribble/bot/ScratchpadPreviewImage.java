package io.github.htearih.scribble.bot;

/**
 * A scratchpad preview PNG, with the validator needed to ask for it again cheaply.
 *
 * @param image        the PNG bytes — 600x420, a 0.6 scale of the 1000x700 canvas. Decode them with
 *                     whatever image library the bot already has
 * @param lastModified the {@code Last-Modified} date of this preview, to pass straight back to
 *                     {@link ScribblePubBot#getScratchpadPreviewImage(String, String)} next time so
 *                     an unchanged canvas costs a 304 instead of a download
 */
public record ScratchpadPreviewImage(byte[] image, String lastModified) {
}
