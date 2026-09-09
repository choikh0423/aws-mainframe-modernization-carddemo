import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { getSession, signOff as signOffRequest, signOn as signOnRequest } from '../api/auth';

/**
 * The signed-on user for the whole shell, mirroring the CDEMO-USER-ID /
 * CDEMO-USER-TYPE fields of the COMMAREA (COCOM01Y). The backend keeps the
 * authoritative COMMAREA in the HTTP session; this context is the screen-side
 * copy so menus and guarded routes can render without a round trip.
 *
 * {@code userType} is the legacy one-character type: 'A' administrator (whose
 * sign-on lands on COADM01C) or 'U' ordinary user (COMEN01C).
 */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [restoring, setRestoring] = useState(true);

  useEffect(() => {
    let cancelled = false;
    getSession()
      .then((session) => {
        if (!cancelled) {
          setUser(session);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setRestoring(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  /**
   * COSGN00C ENTER. Resolves to the same payload the API returns, so the screen
   * can show the legacy ERRMSG itself; on success the context is populated and
   * {@code nextProgram} names the menu program to route to.
   */
  const signOn = useCallback(async (userId, password) => {
    const result = await signOnRequest(userId, password);
    if (result.signedOn) {
      setUser({
        userId: result.userId,
        userType: result.userType,
        nextProgram: result.nextProgram,
      });
    }
    return result;
  }, []);

  /** PF3 from a menu: clear the COMMAREA and return the thank-you message. */
  const signOff = useCallback(async () => {
    const result = await signOffRequest();
    setUser(null);
    return result;
  }, []);

  const value = useMemo(
    () => ({
      user,
      restoring,
      isSignedOn: Boolean(user && user.userId),
      isAdmin: Boolean(user && user.userType === 'A'),
      signOn,
      signOff,
    }),
    [user, restoring, signOn, signOff]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
