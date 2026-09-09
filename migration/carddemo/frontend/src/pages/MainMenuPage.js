import React from 'react';
import MenuScreen from '../components/MenuScreen';
import { getMainMenu } from '../api/auth';

/**
 * CM00 — Main Menu (COMEN01C), the 11 options of COMEN02Y. Options whose
 * copybook user type is 'A' are refused for a 'U' user by the backend with
 * "No access - Admin Only option... " (COMEN01C.cbl:148-158).
 */
export default function MainMenuPage() {
  return (
    <MenuScreen
      tranId="CM00"
      progName="COMEN01C"
      title="Main Menu"
      menu="main"
      loadScreen={getMainMenu}
    />
  );
}
