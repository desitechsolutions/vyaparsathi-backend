package com.desitech.vyaparsathi.common.exception;

public class EntityNotFoundAppException extends ApplicationException {
    public EntityNotFoundAppException(String entity, Object id) {
        super(entity + " not found with ID: " + id);
    }
}
