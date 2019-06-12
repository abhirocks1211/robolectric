package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.M;
import static com.google.common.truth.Truth.assertThat;

import android.telecom.CallAudioState;
import android.telecom.InCallAdapter;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

/** Robolectric test for {@link ShadowInCallAdapter}. */
@RunWith(AndroidJUnit4.class)
@Config(minSdk = M)
public class ShadowInCallAdapterTest {
  InCallAdapter adapter;

  @Before
  public void setUp() {
    adapter = Shadow.newInstanceOf(InCallAdapter.class);
  }

  @Test
  public void setAudioRoute_getAudioRoute() {
    testSetAudioGetAudio(CallAudioState.ROUTE_EARPIECE);
    testSetAudioGetAudio(CallAudioState.ROUTE_SPEAKER);
    testSetAudioGetAudio(CallAudioState.ROUTE_BLUETOOTH);
    testSetAudioGetAudio(CallAudioState.ROUTE_WIRED_HEADSET);
  }

  private void testSetAudioGetAudio(int audioRoute) {
    adapter.setAudioRoute(audioRoute);
    assertThat(((ShadowInCallAdapter) Shadow.extract(adapter)).getAudioRoute())
        .isEqualTo(audioRoute);
  }
}
