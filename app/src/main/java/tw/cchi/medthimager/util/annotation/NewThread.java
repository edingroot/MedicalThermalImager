package tw.cchi.medthimager.util.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Scope;

/**
 * Identifies the target will start a new thread when called.
 */
@Scope
@Retention(RetentionPolicy.SOURCE)
public @interface NewThread {
}
