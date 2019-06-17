package org.robolectric.shadows;

import android.os.Build.VERSION_CODES;
import android.telecom.CallAudioState;
import android.telecom.InCallAdapter;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Shadow for {@link android.telecom.InCallAdapter}. */
@Implements(InCallAdapter.class)
public class ShadowInCallAdapter {

  private int audioRoute = CallAudioState.ROUTE_EARPIECE;

  @Implementation(minSdk = VERSION_CODES.LOLLIPOP)
  protected void setAudioRoute(int route) {
    audioRoute = route;
  }

  public int getAudioRoute() {
    return audioRoute;
  }
}
