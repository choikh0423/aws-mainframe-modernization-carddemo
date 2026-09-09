package com.carddemo.authshell;

/**
 * The colour a program MOVEs into the ERRMSG field's colour attribute.
 *
 * <p>The maps define ERRMSG as {@code COLOR=RED}, so red is what an untouched
 * message shows; COMEN01C overrides it with {@code DFHGREEN} for "coming soon"
 * (COMEN01C.cbl:171) and COADM01C with {@code DFHGREEN} for its not-installed
 * message (COADM01C.cbl:151, 272).
 */
public enum MessageColour {
    RED,
    GREEN
}
