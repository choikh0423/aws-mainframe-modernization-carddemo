package com.carddemo.authshell;

/**
 * The outcome of COMEN01C / COADM01C PROCESS-ENTER-KEY for one typed option.
 *
 * @param accepted      true when the option is dispatched, i.e. the COBOL reached its XCTL
 * @param option        the selected option, or null when the number was rejected
 * @param message       the ERRMSG text; empty when the option was accepted
 * @param messageColour the colour the program MOVEs into ERRMSGC, red being the map default
 * @param optionEcho    the two digits the program MOVEs back into OPTIONO, or null when the
 *                      option field was never parsed (the invalid-key path)
 */
public record MenuSelection(boolean accepted, MenuOption option, String message,
                            MessageColour messageColour, String optionEcho) {

    public static MenuSelection accepted(MenuOption option, String optionEcho) {
        return new MenuSelection(true, option, "", MessageColour.RED, optionEcho);
    }

    public static MenuSelection rejected(String message, MessageColour colour, String optionEcho) {
        return new MenuSelection(false, null, message, colour, optionEcho);
    }

    /** A rejection that keeps the option, for the not-installed / coming-soon messages. */
    public static MenuSelection notDispatched(MenuOption option, String message,
                                              MessageColour colour, String optionEcho) {
        return new MenuSelection(false, option, message, colour, optionEcho);
    }
}
