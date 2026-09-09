import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Guards every screen other than COSGN00C: without a signed-on COMMAREA the
 * shell goes back to the sign-on screen, the way CICS sends an unauthenticated
 * terminal to CC00.
 */
export default function RequireSignOn({ children }) {
  const { isSignedOn, restoring } = useAuth();

  if (restoring) {
    return null;
  }
  if (!isSignedOn) {
    return <Navigate to="/" replace />;
  }
  return children;
}
