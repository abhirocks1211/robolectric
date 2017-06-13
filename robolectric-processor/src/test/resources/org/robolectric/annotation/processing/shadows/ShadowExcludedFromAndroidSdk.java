package org.robolectric.annotation.processing.shadows;

import com.example.objects.Dummy;
import org.robolectric.annotation.Implements;

@Implements(value = Dummy.class, isInAndroidSdk = false)
public class ShadowExcludedFromAndroidSdk {
}
