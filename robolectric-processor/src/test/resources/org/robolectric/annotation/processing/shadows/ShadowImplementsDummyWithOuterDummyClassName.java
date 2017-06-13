package org.robolectric.annotation.processing.shadows;

import com.example.objects.Dummy;
import org.robolectric.annotation.Implements;

@Implements(value = Dummy.class,
            className="com.example.objects.OuterDummy")
public class ShadowImplementsDummyWithOuterDummyClassName {
  
}
