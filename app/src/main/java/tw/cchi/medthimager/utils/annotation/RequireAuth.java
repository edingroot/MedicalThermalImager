package tw.cchi.medthimager.utils.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Scope;

/**
 * Identifies the target api is authentication needed.
 */
@Scope
@Retention(RetentionPolicy.SOURCE)
public @interface RequireAuth {
}
