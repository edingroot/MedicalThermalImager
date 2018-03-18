package tw.cchi.flironedemo1.di;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Scope;

/**
 * Identifies the target will start a new thread when called.
 */
@Scope
@Retention(RetentionPolicy.RUNTIME)
public @interface NewThread {
}
