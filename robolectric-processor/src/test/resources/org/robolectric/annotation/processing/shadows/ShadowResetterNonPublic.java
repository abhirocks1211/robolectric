package org.robolectric.annotation.processing.shadows;

import com.example.objects.Dummy;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

@Implements(Dummy.class)
public class ShadowResetterNonPublic {

  @Resetter
  protected static void resetter_method() {}
}
