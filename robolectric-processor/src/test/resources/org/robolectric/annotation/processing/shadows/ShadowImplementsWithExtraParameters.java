package org.robolectric.annotation.processing.shadows;

import com.example.objects.Dummy;
import org.robolectric.annotation.Implements;

@Implements(Dummy.class)
public class ShadowImplementsWithExtraParameters<T,S,R> {
}
