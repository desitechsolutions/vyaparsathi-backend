package com.desitech.vyaparsathi.common.annotations;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckSubscriptionLimit {
    String value();
}