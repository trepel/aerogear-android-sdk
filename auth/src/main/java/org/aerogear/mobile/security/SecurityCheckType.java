package org.aerogear.mobile.security;

import org.aerogear.mobile.security.checks.DebuggerCheck;
import org.aerogear.mobile.security.checks.EmulatorCheck;
import org.aerogear.mobile.security.checks.RootedCheck;
import org.aerogear.mobile.security.checks.ScreenLockCheck;

/**
 * Checks that can be performed.
 */
public enum SecurityCheckType {
    /**
     *  Detect whether the device is rooted.
     */
    IS_ROOTED(new RootedCheck()),
    IS_DEBUGGER(new DebuggerCheck()),
    IS_EMULATOR(new EmulatorCheck()),
    HAS_SCREENLOCK(new ScreenLockCheck());

    private SecurityCheck check;

    SecurityCheckType(SecurityCheck check) {
        this.check = check;
    }

    /**
     * Return the {@link SecurityCheck} implementation for this check.
     *
     * @return
     */
    public SecurityCheck getSecurityCheck() {
        return check;
    }
}
