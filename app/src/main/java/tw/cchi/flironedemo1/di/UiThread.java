package tw.cchi.flironedemo1.di;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Scope;

/**
 * Identifies the target should be called on the ui thread.
 */
@Scope
@Retention(RetentionPolicy.RUNTIME)
public @interface UiThread {
}
