package app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal;

import android.graphics.Bitmap;

import java.util.ArrayList;

public interface OnPointCreatedListener {
    void OnPointCreated(Bitmap finalBitmap, ArrayList<Dot> points);
}
