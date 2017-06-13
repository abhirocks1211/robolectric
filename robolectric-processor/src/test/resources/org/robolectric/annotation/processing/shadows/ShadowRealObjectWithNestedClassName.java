package org.robolectric.annotation.processing.shadows;

import com.example.objects.OuterDummy;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

@Implements(value=Robolectric.Anything.class,
            className="com.example.objects.OuterDummy$InnerDummy")
public class ShadowRealObjectWithNestedClassName {

  @RealObject OuterDummy.InnerDummy someField;
}
