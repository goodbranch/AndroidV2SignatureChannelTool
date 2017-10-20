package com.branch.v2.read.zip;

/**
 * Indicates that a ZIP archive is not well-formed.
 */
public class ZipFormatException extends Exception {
    private static final long serialVersionUID = 1L;

    public ZipFormatException(String message) {
        super(message);
    }

    public ZipFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}

