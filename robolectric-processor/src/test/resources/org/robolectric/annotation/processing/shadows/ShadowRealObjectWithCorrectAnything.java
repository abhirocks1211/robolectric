package org.robolectric.annotation.processing.shadows;

import com.example.objects.Dummy;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

@Implements(value=Robolectric.Anything.class,
            className="com.example.objects.Dummy"
            )
public class ShadowRealObjectWithCorrectAnything {

  @RealObject Dummy someField;
}
