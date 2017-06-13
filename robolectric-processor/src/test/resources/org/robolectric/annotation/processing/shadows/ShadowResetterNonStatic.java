package org.robolectric.annotation.processing.shadows;

import com.example.objects.Dummy;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

@Implements(Dummy.class)
public class ShadowResetterNonStatic {

  @Resetter
  public void resetter_method() {}
}
