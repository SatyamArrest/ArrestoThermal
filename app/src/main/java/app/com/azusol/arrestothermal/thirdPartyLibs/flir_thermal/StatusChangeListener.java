package app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal;

import com.flir.thermalsdk.live.Identity;

public interface StatusChangeListener {
    /**
     * Will be called when the main activity should re-draw the new frame
     */
    void onFrameUpdated();
    /**
     * Called when the status message changes
     */
    void onStatusUpdated(String text);

    void onNewCameraFound(Identity identity);

    void onCameraConnect(Object status);

    void onCapture(boolean isCapture);
    /**
     * Called when we want to display a toast message
     *
     * @param message the message to show
     **/
    void showMessage(String message);
}
