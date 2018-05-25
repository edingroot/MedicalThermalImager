package tw.cchi.medthimager.util.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Scope;

/**
 * Identifies the target class (model) is required by retrofit api client.
 */
@Scope
@Retention(RetentionPolicy.SOURCE)
public @interface ApiIntegration {
}
