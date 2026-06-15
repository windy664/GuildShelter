package org.windy.guildshelter.persistence;

/** 持久层故障的非受检包装，使 domain 的 port 接口不暴露 SQLException。 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
