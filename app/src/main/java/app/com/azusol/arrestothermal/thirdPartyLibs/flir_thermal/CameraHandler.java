package app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.Nullable;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
import com.flir.thermalsdk.image.ImageBuffer;
import com.flir.thermalsdk.image.TemperatureUnit;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.image.fusion.FusionMode;
import com.flir.thermalsdk.image.palettes.PaletteManager;
import com.flir.thermalsdk.live.Camera;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.ConnectParameters;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.discovery.DiscoveryFactory;
import com.flir.thermalsdk.live.remote.OnReceived;
import com.flir.thermalsdk.live.remote.OnRemoteError;
import com.flir.thermalsdk.live.streaming.Stream;
import com.flir.thermalsdk.live.streaming.ThermalImageStreamListener;
import com.flir.thermalsdk.live.streaming.ThermalStreamer;
import com.flir.thermalsdk.utils.Pair;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class CameraHandler {
    /**
     * Provides an interface to call back to the hosting activity
     */
    // log tag
    private final String TAG = this.getClass().getSimpleName();
    private final StatusChangeListener statusChangeListner;
    private final Camera camera = new Camera();
    private final LinkedList<Identity> foundIdentities = new LinkedList<>();
    // will be updated each time we render a frame
    private Bitmap mostRecentBitmap = null;
    // if you call disconnect while ThermalStreamer's update method
    // is still running, you will get a hard crash.
    // Calling startStreaming while disconnected or while already streaming
    // will throw an exception.
    private final Object updateLock = new Object();

    // the implementation of isotherms depends on the thermal image
    // having received at least one frame. It also needs to be re-added
    // if the temperature range changes

    public CameraHandler(StatusChangeListener onUiUpdated) {
        this.statusChangeListner = onUiUpdated;
    }

    public @Nullable
    Bitmap getMostRecentBitmap() {
        return mostRecentBitmap;
    }


    public void startDiscovering() {
        DiscoveryFactory.getInstance().scan(
                new DiscoveryEventListener() {
                    @Override
                    public void onCameraFound(Identity identity) {
                        foundIdentities.add(identity);
                        statusChangeListner.onNewCameraFound(identity);
                    }

                    @Override
                    public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode error) {
                        // need to implement in a production app
                    }
                }
                , CommunicationInterface.EMULATOR, CommunicationInterface.USB, CommunicationInterface.INTEGRATED);
        statusChangeListner.onStatusUpdated("discovering");
    }

    public void stopDiscovering() {
        DiscoveryFactory.getInstance().stop(CommunicationInterface.EMULATOR, CommunicationInterface.USB, CommunicationInterface.INTEGRATED);
        statusChangeListner.onStatusUpdated("not discovering");
    }


    public void connect() {
        final Identity identity = getIdentity();
        if (identity == null) {
            showMessage("No cameras discovered");
            return;
        }
        if (camera.isConnected()) {
            showMessage("Already connected");
            return;
        }
        Log.e(TAG, "identity: " + identity);
        try {
            camera.connect(identity, new ConnectionStatusListener() {
                @Override
                public void onDisconnected(ErrorCode errorCode) {
                    statusChangeListner.onStatusUpdated("" + errorCode);
                }
            }, new ConnectParameters());
            statusChangeListner.onCameraConnect("status");
        } catch (IOException e) {
            showMessage("Camera failed to connect");
            Log.e(TAG, "Failed to connect", e);
            return;
        }
    }

    public void disconnect() {
        synchronized (updateLock) {
            if (camera.isConnected()) {
                if (!camera.getStreams().isEmpty()) {
                    final Stream stream = camera.getStreams().get(0);
                    if (stream.isStreaming())
                        stream.stop();
                }
                camera.disconnect();
            }
        }
    }


    Camera.Consumer<ThermalImage> handleIncomingImage = new Camera.Consumer<ThermalImage>() {
        @Override
        public void accept(ThermalImage image) {
            image.setPalette(PaletteManager.getDefaultPalettes()
                    .stream()
                    .filter(palette -> palette.name.equalsIgnoreCase("iron"))
                    .findFirst()
                    .orElseGet(() -> PaletteManager.getDefaultPalettes().get(0)));
            image.getFusion().setFusionMode(FusionMode.MSX);
            image.setTemperatureUnit(TemperatureUnit.CELSIUS);
            if (isCapture) {
                try {
                    image.saveAs(imagePath);
                    isCapture = false;
                    statusChangeListner.onCapture(true);
                } catch (IOException e) {
                    statusChangeListner.onCapture(false);
                    Log.e(TAG, "saveThermalimage Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    };

    Camera.Consumer<ThermalImage> forSetup = new Camera.Consumer<ThermalImage>() {
        @Override
        public void accept(ThermalImage image) {
            image.setPalette(PaletteManager.getDefaultPalettes()
                    .stream()
                    .filter(palette -> palette.name.equalsIgnoreCase("iron"))
                    .findFirst()
                    .orElseGet(() -> PaletteManager.getDefaultPalettes().get(0)));
            image.getFusion().setFusionMode(FusionMode.MSX);
            image.setTemperatureUnit(TemperatureUnit.CELSIUS);
        }
    };

    ThermalStreamer streamer;
    boolean isStarted;

    public void startStreaming() {
        synchronized (updateLock) {
            if (!camera.isConnected()) {
                showMessage("Cannot start streaming if not connected");
                return;
            }
            final Stream stream = camera.getStreams().get(0);
            if (stream.isStreaming()) {
                showMessage("Cannot start streaming if already streaming");
                return;
            }
            isStarted = false;
            final Executor renderExecutor = Executors.newSingleThreadExecutor();
            streamer = new ThermalStreamer(stream);
            streamer.withThermalImage(forSetup);
            stream.start(new OnReceived<Void>() {
                @Override
                public void onReceived(Void aVoid) {
                    renderExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (updateLock) {
                                if (!camera.isConnected())
                                    return;
                                streamer.update();
                                streamer.withThermalImage(handleIncomingImage);
                                ImageBuffer renderedImage = streamer.getImage();
                                mostRecentBitmap = BitmapAndroid.createBitmap(renderedImage).getBitMap();
                                statusChangeListner.onFrameUpdated();
                                if (!isStarted) {
                                    isStarted = true;
                                    statusChangeListner.onStatusUpdated("streaming");
                                }
                            }
                        }
                    });
                }
            }, new OnRemoteError() {
                @Override
                public void onRemoteError(ErrorCode errorCode) {
                    Log.e(TAG, "Error starting stream: " + errorCode);
                    showMessage("Error starting stream: " + errorCode);
                    statusChangeListner.onStatusUpdated("streamingError");
                }
            });
        }

    }

    boolean isCapture;
    String imagePath = "";

    public void saveThermalimage(String path) {
        imagePath = path;
        isCapture = true;
//        try {
//            String thermal_path = Static_values.directory + "/saved_images/" + "thermal_" + System.currentTimeMillis() + ".jpg";
////            recentThermalImage.saveAs(thermal_path);
//        } catch (IOException e) {
//            e.printStackTrace();
//            Log.e(TAG, "saveThermalimage Error: " + e.getMessage());
//        }
    }

    public void stopStreaming() {
        synchronized (updateLock) {
            if (!camera.isConnected()) {
                showMessage("Cannot stop streaming if not connected");
                return;
            }
            camera.unsubscribeStream(streamListener);
//            final Stream stream = camera.getStreams().get(0);
//            if (stream.isStreaming())
//                stream.stop();

            String name = "Flir_" + System.currentTimeMillis();
        }

    }

    ThermalImageStreamListener streamListener = new ThermalImageStreamListener() {
        @Override
        public void onImageReceived() {
            camera.withImage(handleIncomingImage);
        }
    };

    @Nullable
    private Identity getIdentity() {
        // assign a priority to each kind of camera
        Function<Identity, Integer> getPriority = id ->
        {
            switch (id.communicationInterface) {
                case INTEGRATED:
                    return 10;
                case USB:
                    return 9;
                case EMULATOR:
                    return 0;
                default:
                    return 1;
            }
        };

        return foundIdentities
                .stream()
                .map(id -> new Pair<>(getPriority.apply(id), id))
                // sort backwards by priority (higher first)
                .sorted((a, b) -> b.first.compareTo(a.first))
                .map(pair -> pair.second)
                .findFirst()
                .orElse(null);
    }

    /**
     * Show a message in the UI and log it to logcat
     *
     * @param message the message to show
     */
    private void showMessage(String message) {
        Log.e(TAG, message);
        statusChangeListner.showMessage(message);
    }
}
