package com.carddemo.authshell;

/**
 * The outcome of COMEN01C / COADM01C PROCESS-ENTER-KEY for one typed option.
 *
 * @param accepted    true when the option is valid and the signed-on user may use it
 * @param option      the selected option, or null when the number was rejected
 * @param message     the screen message; empty when the option was accepted
 */
public record MenuSelection(boolean accepted, MenuOption option, String message) {

    public static MenuSelection accepted(MenuOption option) {
        return new MenuSelection(true, option, "");
    }

    public static MenuSelection rejected(String message) {
        return new MenuSelection(false, null, message);
    }
}
