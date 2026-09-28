package com.rayhan.base.service;
/**
 * Service interface for Check if Email is Already Registered.
 */
public interface CheckEmailService {

    /**
     * Check if Email is Already Registered.
     *
     * @param email Email of the user.
     * @return exists true or false
     */
    boolean emailExists(String email);
}