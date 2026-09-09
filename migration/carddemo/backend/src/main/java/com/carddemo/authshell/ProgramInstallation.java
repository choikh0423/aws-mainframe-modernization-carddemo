package com.carddemo.authshell;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Answers what {@code EXEC CICS INQUIRE PROGRAM} answered on the mainframe: is
 * the program the menu is about to transfer to defined to the system?
 *
 * <p>COMEN01C asks explicitly before option 11 (COMEN01C.cbl:147-152) and
 * COADM01C finds out the hard way, through the {@code PGMIDERR} condition its
 * {@code HANDLE CONDITION} traps (COADM01C.cbl:77-79, 270-281). Both exist
 * because {@code app/csd/CARDDEMO.CSD} defines the base group only: COPAUS0C
 * ships with the authorization add-on and COTRTLIC/COTRTUPC with the Db2 add-on.
 *
 * <p>The consolidated app is one deployment that carries the whole estate (D-1,
 * D-4), so every program is installed by default. A deployment that leaves an
 * add-on out lists its programs in {@code carddemo.authshell.uninstalled-programs}
 * and the menus answer exactly as the CSD-less region did.
 */
@Component
public class ProgramInstallation {

    private final Set<String> uninstalled;

    public ProgramInstallation(
            @Value("${carddemo.authshell.uninstalled-programs:}") String uninstalledPrograms) {
        this.uninstalled = Arrays.stream(uninstalledPrograms.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> name.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isInstalled(String programName) {
        return programName != null
                && !uninstalled.contains(programName.trim().toUpperCase(Locale.ROOT));
    }
}
