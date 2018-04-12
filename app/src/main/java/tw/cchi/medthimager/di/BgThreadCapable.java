package tw.cchi.medthimager.di;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Scope;

/**
 * Identifies the target is capable of executing on a background thread.
 */
@Scope
@Retention(RetentionPolicy.SOURCE)
public @interface BgThreadCapable {
}
