package org.robolectric.shadows;

import static android.location.LocationManager.PASSIVE_PROVIDER;
import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.P;
import static android.provider.Settings.Secure.LOCATION_MODE;
import static android.provider.Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
import static android.provider.Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
import static android.provider.Settings.Secure.LOCATION_MODE_OFF;
import static android.provider.Settings.Secure.LOCATION_MODE_SENSORS_ONLY;
import static android.provider.Settings.Secure.LOCATION_PROVIDERS_ALLOWED;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings.Secure;
import android.support.annotation.GuardedBy;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;

/**
 * Shadow for {@link LocationManager}. Note that the default state of location on Android devices is
 * location on, gps provider enabled, network provider disabled.
 */
@SuppressWarnings("deprecation")
@Implements(value = LocationManager.class, looseSignatures = true)
public class ShadowLocationManager {

  /** Properties of a provider. */
  public static class ProviderProperties {
    private final boolean requiresNetwork;
    private final boolean requiresSatellite;
    private final boolean requiresCell;
    private final boolean hasMonetaryCost;
    private final boolean supportsAltitude;
    private final boolean supportsSpeed;
    private final boolean supportsBearing;
    private final int powerRequirement;
    private final int accuracy;

    public ProviderProperties(
        boolean requiresNetwork,
        boolean requiresSatellite,
        boolean requiresCell,
        boolean hasMonetaryCost,
        boolean supportsAltitude,
        boolean supportsSpeed,
        boolean supportsBearing,
        int powerRequirement,
        int accuracy) {
      this.requiresNetwork = requiresNetwork;
      this.requiresSatellite = requiresSatellite;
      this.requiresCell = requiresCell;
      this.hasMonetaryCost = hasMonetaryCost;
      this.supportsAltitude = supportsAltitude;
      this.supportsSpeed = supportsSpeed;
      this.supportsBearing = supportsBearing;
      this.powerRequirement = powerRequirement;
      this.accuracy = accuracy;
    }

    public ProviderProperties(Criteria criteria) {
      this.requiresNetwork = false;
      this.requiresSatellite = false;
      this.requiresCell = false;
      this.hasMonetaryCost = criteria.isCostAllowed();
      this.supportsAltitude = criteria.isAltitudeRequired();
      this.supportsSpeed = criteria.isSpeedRequired();
      this.supportsBearing = criteria.isBearingRequired();
      this.powerRequirement = criteria.getPowerRequirement();
      this.accuracy = criteria.getAccuracy();
    }

    private boolean meetsCriteria(Criteria criteria) {
      if (criteria.getAccuracy() != Criteria.NO_REQUIREMENT && criteria.getAccuracy() < accuracy) {
        return false;
      }
      if (criteria.getPowerRequirement() != Criteria.NO_REQUIREMENT
          && criteria.getPowerRequirement() < powerRequirement) {
        return false;
      }
      if (criteria.isAltitudeRequired() && !supportsAltitude) {
        return false;
      }
      if (criteria.isSpeedRequired() && !supportsSpeed) {
        return false;
      }
      if (criteria.isBearingRequired() && !supportsBearing) {
        return false;
      }
      if (!criteria.isCostAllowed() && hasMonetaryCost) {
        return false;
      }
      return true;
    }
  }

  @Nullable private static Constructor<LocationProvider> locationProviderConstructor;

  @RealObject private LocationManager realLocationManager;

  @GuardedBy("providers")
  private final HashSet<ProviderEntry> providers = new HashSet<>();

  @GuardedBy("gpsStatusListeners")
  private final HashSet<GpsStatus.Listener> gpsStatusListeners = new HashSet<>();

  private boolean passiveProviderEnabled = true;

  public ShadowLocationManager() {
    // create default providers
    providers.add(
        new ProviderEntry(
            LocationManager.GPS_PROVIDER,
            new ProviderProperties(
                true,
                true,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_HIGH,
                Criteria.ACCURACY_FINE)));
    providers.add(
        new ProviderEntry(
            LocationManager.NETWORK_PROVIDER,
            new ProviderProperties(
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_COARSE)));
    providers.add(
        new ProviderEntry(
            PASSIVE_PROVIDER,
            new ProviderProperties(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_COARSE)));
  }

  @Implementation
  protected List<String> getAllProviders() {
    synchronized (providers) {
      ArrayList<String> allProviders = new ArrayList<>(providers.size());
      for (ProviderEntry providerEntry : providers) {
        allProviders.add(providerEntry.name);
      }
      return allProviders;
    }
  }

  @Implementation
  @Nullable
  protected LocationProvider getProvider(String name) {
    if (RuntimeEnvironment.getApiLevel() < KITKAT) {
      // jelly bean has no way to properly construct a LocationProvider, we give up
      return null;
    }

    ProviderEntry providerEntry = getProviderEntry(name);
    if (providerEntry == null) {
      return null;
    }

    try {
      synchronized (ShadowLocationManager.class) {
        if (locationProviderConstructor == null) {
          locationProviderConstructor =
              LocationProvider.class.getConstructor(
                  String.class, com.android.internal.location.ProviderProperties.class);
          locationProviderConstructor.setAccessible(true);
        }
      }
      return locationProviderConstructor.newInstance(name, providerEntry.createRealProperties());
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  @Implementation
  protected List<String> getProviders(boolean enabledOnly) {
    return getProviders(null, enabledOnly);
  }

  @Implementation
  protected List<String> getProviders(@Nullable Criteria criteria, boolean enabled) {
    synchronized (providers) {
      ArrayList<String> matchingProviders = new ArrayList<>(providers.size());
      for (ProviderEntry providerEntry : providers) {
        if (enabled && !isProviderEnabled(providerEntry.name)) {
          continue;
        }
        if (criteria != null && !providerEntry.meetsCriteria(criteria)) {
          continue;
        }
        matchingProviders.add(providerEntry.name);
      }
      return matchingProviders;
    }
  }

  @Implementation
  @Nullable
  protected String getBestProvider(Criteria criteria, boolean enabled) {
    List<String> providers = getProviders(criteria, enabled);
    if (providers.isEmpty()) {
      providers = getProviders(null, enabled);
    }

    if (!providers.isEmpty()) {
      if (providers.contains(LocationManager.GPS_PROVIDER)) {
        return LocationManager.GPS_PROVIDER;
      } else if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
        return LocationManager.NETWORK_PROVIDER;
      } else {
        return providers.get(0);
      }
    }

    return null;
  }

  @Implementation
  protected boolean isProviderEnabled(String provider) {
    if (RuntimeEnvironment.getApiLevel() >= P) {
      if (!isLocationEnabled()) {
        return false;
      }
    }

    if (PASSIVE_PROVIDER.equals(provider)) {
      return passiveProviderEnabled;
    } else {
      return getAllowedProviders().contains(provider);
    }
  }

  private List<String> getAllowedProviders() {
    String allowedProviders =
        Secure.getString(getContext().getContentResolver(), LOCATION_PROVIDERS_ALLOWED);
    if (TextUtils.isEmpty(allowedProviders)) {
      return Collections.emptyList();
    } else {
      return Arrays.asList(allowedProviders.split(","));
    }
  }

  /** Completely removes a provider. */
  public void removeProvider(String name) {
    removeProviderEntry(name);
  }

  /**
   * Sets the properties of the given provider. The provider will be created if it doesn't exist
   * already.
   */
  public void setProviderProperties(String name, @Nullable ProviderProperties properties) {
    if (name == null) {
      throw new NullPointerException();
    }
    getOrCreateProviderEntry(name).properties = properties;
  }

  /**
   * Sets the given provider enabled or disabled. The provider will be created if it doesn't exist
   * already. On P and above, location must also be enabled via {@link #setLocationEnabled(boolean)}
   * in order for a provider to be considered enabled.
   */
  public void setProviderEnabled(String name, boolean enabled) {
    ProviderEntry providerEntry = getOrCreateProviderEntry(name);

    if (PASSIVE_PROVIDER.equals(name)) {
      // the passive provider cannot be disabled, but the passive provider didn't exist in previous
      // versions of this shadow. for backwards compatibility, we let the passive provider be
      // disabled. this also help emulate the situation where an app only has COARSE permissions,
      // which this shadow normally can't emulate.
      passiveProviderEnabled = enabled;
      return;
    }

    int oldLocationMode = getLocationMode();
    int newLocationMode = oldLocationMode;
    if (RuntimeEnvironment.getApiLevel() < P) {
      if (LocationManager.GPS_PROVIDER.equals(name)) {
        if (enabled) {
          switch (oldLocationMode) {
            case LOCATION_MODE_OFF:
              newLocationMode = LOCATION_MODE_SENSORS_ONLY;
              break;
            case LOCATION_MODE_BATTERY_SAVING:
              newLocationMode = LOCATION_MODE_HIGH_ACCURACY;
              break;
            default:
              break;
          }
        } else {
          switch (oldLocationMode) {
            case LOCATION_MODE_SENSORS_ONLY:
              newLocationMode = LOCATION_MODE_OFF;
              break;
            case LOCATION_MODE_HIGH_ACCURACY:
              newLocationMode = LOCATION_MODE_BATTERY_SAVING;
              break;
            default:
              break;
          }
        }
      } else if (LocationManager.NETWORK_PROVIDER.equals(name)) {
        if (enabled) {
          switch (oldLocationMode) {
            case LOCATION_MODE_OFF:
              newLocationMode = LOCATION_MODE_BATTERY_SAVING;
              break;
            case LOCATION_MODE_SENSORS_ONLY:
              newLocationMode = LOCATION_MODE_HIGH_ACCURACY;
              break;
            default:
              break;
          }
        } else {
          switch (oldLocationMode) {
            case LOCATION_MODE_BATTERY_SAVING:
              newLocationMode = LOCATION_MODE_OFF;
              break;
            case LOCATION_MODE_HIGH_ACCURACY:
              newLocationMode = LOCATION_MODE_SENSORS_ONLY;
              break;
            default:
              break;
          }
        }
      }
    }

    if (newLocationMode != oldLocationMode) {
      setLocationModeInternal(newLocationMode);
    } else {
      ShadowSettings.ShadowSecure.updateEnabledProviders(
          getContext().getContentResolver(), name, enabled);
    }

    if (providerEntry != null) {
      for (ProviderEntry.ListenerEntry listener : providerEntry.listeners) {
        if (enabled) {
          listener.invokeOnProviderEnabled(name);
        } else {
          listener.invokeOnProviderDisabled(name);
        }
      }
    }
  }

  // @SystemApi
  @Implementation(minSdk = P)
  protected boolean isLocationEnabledForUser(UserHandle userHandle) {
    return isLocationEnabled();
  }

  private boolean isLocationEnabled() {
    return getLocationMode() != LOCATION_MODE_OFF;
  }

  // @SystemApi
  @Implementation(minSdk = P)
  protected void setLocationEnabledForUser(boolean enabled, UserHandle userHandle) {
    setLocationModeInternal(enabled ? LOCATION_MODE_HIGH_ACCURACY : LOCATION_MODE_OFF);
  }

  /**
   * On P and above, turns location on or off. On pre-P devices, sets the location mode to {@link
   * android.provider.Settings.Secure#LOCATION_MODE_HIGH_ACCURACY} or {@link
   * android.provider.Settings.Secure#LOCATION_MODE_OFF}.
   */
  public void setLocationEnabled(boolean enabled) {
    setLocationEnabledForUser(enabled, Process.myUserHandle());
  }

  private int getLocationMode() {
    return Secure.getInt(getContext().getContentResolver(), LOCATION_MODE, LOCATION_MODE_OFF);
  }

  /**
   * On pre-P devices, sets the device location mode. For P and above, use {@link
   * #setLocationEnabled(boolean)} and {@link #setProviderEnabled(String, boolean)} in combination
   * to achieve the desired effect.
   */
  public void setLocationMode(int locationMode) {
    if (RuntimeEnvironment.getApiLevel() >= P) {
      throw new AssertionError(
          "Tests may not set location mode directly on P and above. Instead, use"
              + " setLocationEnabled() and setProviderEnabled() in combination to achieve the"
              + " desired result.");
    }

    setLocationModeInternal(locationMode);
  }

  private void setLocationModeInternal(int locationMode) {
    Secure.putInt(getContext().getContentResolver(), LOCATION_MODE, locationMode);
  }

  @Implementation
  @Nullable
  protected Location getLastKnownLocation(String provider) {
    ProviderEntry providerEntry = getProviderEntry(provider);
    if (providerEntry == null) {
      return null;
    }

    return providerEntry.lastLocation;
  }

  /** Sets the last known location for the given provider. */
  public void setLastKnownLocation(String provider, @Nullable Location location) {
    getOrCreateProviderEntry(provider).lastLocation = location;
  }

  @Implementation
  protected void requestSingleUpdate(
      String provider, LocationListener listener, @Nullable Looper looper) {
    LocationRequest request = new LocationRequest(provider, 0, 0, true);
    requestLocationUpdates(request, listener, looper, null);
  }

  @Implementation
  protected void requestSingleUpdate(
      Criteria criteria, LocationListener listener, @Nullable Looper looper) {
    String bestProvider = getBestProvider(criteria, true);
    if (bestProvider == null) {
      throw new IllegalArgumentException("no providers found for criteria");
    }
    LocationRequest request = new LocationRequest(bestProvider, 0, 0, true);
    requestLocationUpdates(request, listener, looper, null);
  }

  @Implementation
  protected void requestSingleUpdate(String provider, PendingIntent intent) {
    LocationRequest request = new LocationRequest(provider, 0, 0, true);
    requestLocationUpdates(request, null, null, intent);
  }

  @Implementation
  protected void requestSingleUpdate(Criteria criteria, PendingIntent intent) {
    String bestProvider = getBestProvider(criteria, true);
    if (bestProvider == null) {
      throw new IllegalArgumentException("no providers found for criteria");
    }
    LocationRequest request = new LocationRequest(bestProvider, 0, 0, true);
    requestLocationUpdates(request, null, null, intent);
  }

  @Implementation
  protected void requestLocationUpdates(
      String provider, long minTime, float minDistance, LocationListener listener) {
    LocationRequest request = new LocationRequest(provider, minTime, minDistance, false);
    requestLocationUpdates(request, listener, null, null);
  }

  @Implementation
  protected void requestLocationUpdates(
      String provider,
      long minTime,
      float minDistance,
      LocationListener listener,
      @Nullable Looper looper) {
    LocationRequest request = new LocationRequest(provider, minTime, minDistance, false);
    requestLocationUpdates(request, listener, looper, null);
  }

  @Implementation
  protected void requestLocationUpdates(
      long minTime,
      float minDistance,
      Criteria criteria,
      LocationListener listener,
      @Nullable Looper looper) {
    String bestProvider = getBestProvider(criteria, true);
    if (bestProvider == null) {
      throw new IllegalArgumentException("no providers found for criteria");
    }
    LocationRequest request = new LocationRequest(bestProvider, minTime, minDistance, false);
    requestLocationUpdates(request, listener, looper, null);
  }

  @Implementation
  protected void requestLocationUpdates(
      String provider, long minTime, float minDistance, PendingIntent intent) {
    LocationRequest request = new LocationRequest(provider, minTime, minDistance, false);
    requestLocationUpdates(request, null, null, intent);
  }

  @Implementation
  protected void requestLocationUpdates(
      long minTime, float minDistance, Criteria criteria, PendingIntent intent) {
    String bestProvider = getBestProvider(criteria, true);
    if (bestProvider == null) {
      throw new IllegalArgumentException("no providers found for criteria");
    }
    LocationRequest request = new LocationRequest(bestProvider, minTime, minDistance, false);
    requestLocationUpdates(request, null, null, intent);
  }

  @Implementation(minSdk = LOLLIPOP)
  protected void requestLocationUpdates(
      @Nullable Object oRequest, Object listener, @Nullable Object looper) {
    android.location.LocationRequest ogRequest = (android.location.LocationRequest) oRequest;
    if (ogRequest == null) {
      ogRequest = new android.location.LocationRequest();
    }
    LocationRequest request =
        new LocationRequest(
            ogRequest.getProvider(),
            ogRequest.getFastestInterval(),
            ogRequest.getSmallestDisplacement(),
            ogRequest.getNumUpdates() == 1);
    requestLocationUpdates(request, (LocationListener) listener, (Looper) looper, null);
  }

  @Implementation(minSdk = LOLLIPOP)
  protected void requestLocationUpdates(@Nullable Object oRequest, Object intent) {
    android.location.LocationRequest ogRequest = (android.location.LocationRequest) oRequest;
    if (ogRequest == null) {
      ogRequest = new android.location.LocationRequest();
    }
    LocationRequest request =
        new LocationRequest(
            ogRequest.getProvider(),
            ogRequest.getFastestInterval(),
            ogRequest.getSmallestDisplacement(),
            ogRequest.getNumUpdates() == 1);
    requestLocationUpdates(request, null, null, (PendingIntent) intent);
  }

  private void requestLocationUpdates(
      LocationRequest request,
      @Nullable LocationListener locationListener,
      @Nullable Looper looper,
      @Nullable PendingIntent pendingIntent) {
    if (locationListener == null && pendingIntent == null) {
      throw new IllegalArgumentException("must supply listener or pending intent");
    }
    if (locationListener != null && looper == null) {
      looper = Looper.getMainLooper();
    }

    getOrCreateProviderEntry(request.provider)
        .addListener(locationListener, looper, pendingIntent, request);
  }

  @Implementation
  protected void removeUpdates(LocationListener listener) {
    removeListener(listener, null);
  }

  @Implementation
  protected void removeUpdates(PendingIntent pendingIntent) {
    removeListener(null, pendingIntent);
  }

  @Implementation(minSdk = P)
  protected boolean injectLocation(Location location) {
    return false;
  }

  @Implementation
  protected boolean addGpsStatusListener(GpsStatus.Listener listener) {
    synchronized (gpsStatusListeners) {
      gpsStatusListeners.add(listener);
    }
    return true;
  }

  @Implementation
  protected void removeGpsStatusListener(GpsStatus.Listener listener) {
    synchronized (gpsStatusListeners) {
      gpsStatusListeners.remove(listener);
    }
  }

  public List<GpsStatus.Listener> getGpsStatusListeners() {
    synchronized (gpsStatusListeners) {
      return new ArrayList<>(gpsStatusListeners);
    }
  }

  /** @deprecated Use {@link #getLocationUpdateListeners()} instead. */
  @Deprecated
  public List<LocationListener> getRequestLocationUpdateListeners() {
    return new ArrayList<>(getLocationUpdateListeners());
  }

  /**
   * Delivers a new location to the appropriate listeners and updates state accordingly. Delivery
   * will ignore the enabled/disabled state of providers, unlike location on a real device.
   */
  public void simulateLocation(Location location) {
    if (location == null) {
      throw new NullPointerException();
    }

    ProviderEntry providerEntry = getOrCreateProviderEntry(location.getProvider());
    if (providerEntry != null && !PASSIVE_PROVIDER.equals(providerEntry.name)) {
      providerEntry.simulateLocation(location);
    }

    ProviderEntry passiveProviderEntry = getProviderEntry(PASSIVE_PROVIDER);
    if (passiveProviderEntry != null) {
      passiveProviderEntry.simulateLocation(location);
    }
  }

  /** Retrieves a list of all currently registered listeners. */
  public List<LocationListener> getLocationUpdateListeners() {
    synchronized (providers) {
      ArrayList<LocationListener> listeners = new ArrayList<>(providers.size());
      for (ProviderEntry providerEntry : providers) {
        for (ProviderEntry.ListenerEntry listenerEntry : providerEntry.listeners) {
          if (listenerEntry.locationListener != null
              && !listeners.contains(listenerEntry.locationListener)) {
            listeners.add(listenerEntry.locationListener);
          }
        }
      }
      return listeners;
    }
  }

  /** Retrieves a list of all currently registered listeners for the given provider. */
  public List<LocationListener> getLocationUpdateListeners(String provider) {
    ProviderEntry providerEntry = getProviderEntry(provider);
    if (providerEntry == null) {
      return Collections.emptyList();
    }

    ArrayList<LocationListener> listeners = new ArrayList<>(providerEntry.listeners.size());
    for (ProviderEntry.ListenerEntry listenerEntry : providerEntry.listeners) {
      if (listenerEntry.locationListener != null) {
        listeners.add(listenerEntry.locationListener);
      }
    }
    return listeners;
  }

  /** Retrieves a list of all currently registered pending intents. */
  public List<PendingIntent> getLocationUpdatePendingIntents() {
    synchronized (providers) {
      ArrayList<PendingIntent> pendingIntents = new ArrayList<>(providers.size());
      for (ProviderEntry providerEntry : providers) {
        for (ProviderEntry.ListenerEntry listenerEntry : providerEntry.listeners) {
          if (listenerEntry.pendingIntent != null
              && !pendingIntents.contains(listenerEntry.pendingIntent)) {
            pendingIntents.add(listenerEntry.pendingIntent);
          }
        }
      }
      return pendingIntents;
    }
  }

  /** Retrieves a list of all currently registered pending intents for the given provider. */
  public List<PendingIntent> getLocationUpdatePendingIntents(String provider) {
    ProviderEntry providerEntry = getProviderEntry(provider);
    if (providerEntry == null) {
      return Collections.emptyList();
    }

    ArrayList<PendingIntent> listeners = new ArrayList<>(providerEntry.listeners.size());
    for (ProviderEntry.ListenerEntry listenerEntry : providerEntry.listeners) {
      if (listenerEntry.pendingIntent != null) {
        listeners.add(listenerEntry.pendingIntent);
      }
    }
    return listeners;
  }

  private Context getContext() {
    return ReflectionHelpers.getField(realLocationManager, "mContext");
  }

  private void removeListener(
      @Nullable LocationListener locationListener, @Nullable PendingIntent pendingIntent) {
    synchronized (providers) {
      for (ProviderEntry providerEntry : providers) {
        providerEntry.removeListener(locationListener, pendingIntent);
      }
    }
  }

  private ProviderEntry getOrCreateProviderEntry(String name) {
    if (name == null) {
      throw new IllegalArgumentException("cannot use a null provider");
    }

    synchronized (providers) {
      ProviderEntry providerEntry = getProviderEntry(name);
      if (providerEntry == null) {
        providerEntry = new ProviderEntry(name, null);
        providers.add(providerEntry);
      }
      return providerEntry;
    }
  }

  @Nullable
  private ProviderEntry getProviderEntry(String name) {
    if (name == null) {
      return null;
    }

    synchronized (providers) {
      for (ProviderEntry providerEntry : providers) {
        if (name.equals(providerEntry.name)) {
          return providerEntry;
        }
      }
    }

    return null;
  }

  private void removeProviderEntry(String name) {
    synchronized (providers) {
      ProviderEntry providerEntry = getProviderEntry(name);
      providers.remove(providerEntry);
    }
  }

  private final class ProviderEntry {
    private final String name;
    private final CopyOnWriteArraySet<ListenerEntry> listeners;

    @Nullable private volatile ProviderProperties properties;
    private Location lastLocation;

    private ProviderEntry(String name, @Nullable ProviderProperties properties) {
      this.name = name;
      listeners = new CopyOnWriteArraySet<>();
      this.properties = properties;
    }

    public void simulateLocation(Location location) {
      lastLocation = new Location(location);

      for (ListenerEntry listenerEntry : listeners) {
        listenerEntry.simulateLocation(location);
      }
    }

    public boolean meetsCriteria(Criteria criteria) {
      if (PASSIVE_PROVIDER.equals(name)) {
        return false;
      }

      ProviderProperties myProperties = properties;
      if (myProperties == null) {
        return false;
      }
      return myProperties.meetsCriteria(criteria);
    }

    public com.android.internal.location.ProviderProperties createRealProperties() {
      ProviderProperties myProperties = properties;
      if (myProperties == null) {
        return null;
      } else {
        return new com.android.internal.location.ProviderProperties(
            myProperties.requiresNetwork,
            myProperties.requiresSatellite,
            myProperties.requiresCell,
            myProperties.hasMonetaryCost,
            myProperties.supportsAltitude,
            myProperties.supportsSpeed,
            myProperties.supportsBearing,
            myProperties.powerRequirement,
            myProperties.accuracy);
      }
    }

    public void addListener(
        @Nullable LocationListener locationListener,
        Looper looper,
        @Nullable PendingIntent pendingIntent,
        LocationRequest request) {
      final ListenerEntry entry;
      if (locationListener != null) {
        entry = new ListenerEntry(locationListener, looper, request);
      } else if (pendingIntent != null) {
        entry = new ListenerEntry(pendingIntent, request);
      } else {
        entry = null;
      }

      if (entry != null) {
        listeners.add(entry);
      }
    }

    public void removeListener(
        @Nullable LocationListener locationListener, @Nullable PendingIntent pendingIntent) {
      for (ListenerEntry listenerEntry : listeners) {
        if (listenerEntry.locationListener == locationListener
            && Objects.equals(listenerEntry.pendingIntent, pendingIntent)) {
          listeners.remove(listenerEntry);
        }
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ProviderEntry)) {
        return false;
      }
      ProviderEntry that = (ProviderEntry) o;
      return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name);
    }

    private final class ListenerEntry {
      private final PendingIntent pendingIntent;
      private final LocationListener locationListener;
      private final Handler handler;

      private final LocationRequest locationRequest;

      @Nullable Location lastDeliveredLocation;

      private ListenerEntry(PendingIntent pendingIntent, LocationRequest locationRequest) {
        this.pendingIntent = pendingIntent;
        locationListener = null;
        handler = null;
        this.locationRequest = locationRequest;
      }

      private ListenerEntry(
          LocationListener locationListener,
          Looper looper,
          LocationRequest locationRequest) {
        this.locationListener = locationListener;
        pendingIntent = null;
        handler = new Handler(looper);
        this.locationRequest = locationRequest;
      }

      public void simulateLocation(Location location) {
        if (lastDeliveredLocation != null) {
          if (location.getTime() - lastDeliveredLocation.getTime() < locationRequest.minTime) {
            return;
          }
          if (distanceBetween(location, lastDeliveredLocation) < locationRequest.minDistance) {
            return;
          }
        }

        lastDeliveredLocation = new Location(location);

        if (locationRequest.singleShot) {
          listeners.remove(this);
        }

        if (locationListener != null) {
          handler.post(() -> locationListener.onLocationChanged(new Location(location)));
        } else if (pendingIntent != null) {
          Intent intent = new Intent();
          intent.putExtra(LocationManager.KEY_LOCATION_CHANGED, new Location(location));
          try {
            pendingIntent.send(getContext(), 0, intent);
          } catch (CanceledException e) {
            removeListener(null, pendingIntent);
          }
        }
      }

      public void invokeOnProviderEnabled(String provider) {
        if (locationListener != null) {
          handler.post(() -> locationListener.onProviderEnabled(provider));
        } else if (pendingIntent != null) {
          Intent intent = new Intent();
          intent.putExtra(LocationManager.KEY_PROVIDER_ENABLED, true);
          try {
            pendingIntent.send(getContext(), 0, intent);
          } catch (CanceledException e) {
            removeListener(null, pendingIntent);
          }
        }
      }

      public void invokeOnProviderDisabled(String provider) {
        if (locationListener != null) {
          handler.post(() -> locationListener.onProviderDisabled(provider));
        } else if (pendingIntent != null) {
          Intent intent = new Intent();
          intent.putExtra(LocationManager.KEY_PROVIDER_ENABLED, false);
          try {
            pendingIntent.send(getContext(), 0, intent);
          } catch (CanceledException e) {
            removeListener(null, pendingIntent);
          }
        }
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (!(o instanceof ListenerEntry)) {
          return false;
        }
        ListenerEntry that = (ListenerEntry) o;
        return Objects.equals(pendingIntent, that.pendingIntent)
            && Objects.equals(locationListener, that.locationListener);
      }

      @Override
      public int hashCode() {
        return Objects.hash(pendingIntent, locationListener);
      }
    }
  }

  // LocationRequest doesn't exist on JB, so we can't use the platform version
  private static class LocationRequest {

    private final String provider;
    private final long minTime;
    private final float minDistance;
    private final boolean singleShot;

    private LocationRequest(String provider, long minTime, float minDistance, boolean singleShot) {
      this.provider = provider;
      this.minTime = minTime;
      this.minDistance = minDistance;
      this.singleShot = singleShot;
    }
  }

  /**
   * Returns the distance between the two locations in meters. Adapted from:
   * http://stackoverflow.com/questions/837872/calculate-distance-in-meters-when-you-know-longitude-and-latitude-in-java
   */
  private static float distanceBetween(Location location1, Location location2) {
    double earthRadius = 3958.75;
    double latDifference = Math.toRadians(location2.getLatitude() - location1.getLatitude());
    double lonDifference = Math.toRadians(location2.getLongitude() - location1.getLongitude());
    double a =
        Math.sin(latDifference / 2) * Math.sin(latDifference / 2)
            + Math.cos(Math.toRadians(location1.getLatitude()))
                * Math.cos(Math.toRadians(location2.getLatitude()))
                * Math.sin(lonDifference / 2)
                * Math.sin(lonDifference / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    double dist = Math.abs(earthRadius * c);

    int meterConversion = 1609;

    return (float) (dist * meterConversion);
  }
}
