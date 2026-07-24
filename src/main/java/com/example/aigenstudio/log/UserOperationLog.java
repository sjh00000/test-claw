package com.example.aigenstudio.log;

import com.example.aigenstudio.domain.OperationTypeEnum;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserOperationLog {

    OperationTypeEnum operationType();
}
