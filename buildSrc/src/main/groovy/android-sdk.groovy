class AndroidSdk {
    final public int apiLevel
    final public String version

    AndroidSdk(int apiLevel, String version) {
        this.version = version
        this.apiLevel = apiLevel
    }

    public static final JELLY_BEAN = new AndroidSdk(16, "4.1.2_r1-robolectric-0")
    public static final JELLY_BEAN_MR1 = new AndroidSdk(17, "4.2.2_r1.2-robolectric-0")
    public static final JELLY_BEAN_MR2 = new AndroidSdk(18, "4.3_r2-robolectric-0")
    public static final KITKAT = new AndroidSdk(19, "4.4_r1-robolectric-1")
    public static final LOLLIPOP = new AndroidSdk(21, "5.0.0_r2-robolectric-1")
    public static final LOLLIPOP_MR1 = new AndroidSdk(22, "5.1.1_r9-robolectric-1")
    public static final M = new AndroidSdk(23, "6.0.0_r1-robolectric-0")

    public static final AndroidSdk[] ALL = [
            JELLY_BEAN,
            JELLY_BEAN_MR1,
            JELLY_BEAN_MR2,
            KITKAT,
            LOLLIPOP,
            LOLLIPOP_MR1,
            M,
    ]

    public static AndroidSdk get(int apiLevel) {
        return ALL.find { it.apiLevel == apiLevel }
    }

    public static final AndroidSdk LATEST_VERSION = M
}