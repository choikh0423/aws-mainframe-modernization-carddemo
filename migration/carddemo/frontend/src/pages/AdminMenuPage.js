import React from 'react';
import MenuScreen from '../components/MenuScreen';
import { getAdminMenu } from '../api/auth';

/**
 * CA00 — Admin Menu (COADM01C), the 6 options of COADM02Y. Reached by signing
 * on as a type 'A' user; every option is administrator-only.
 */
export default function AdminMenuPage() {
  return (
    <MenuScreen
      tranId="CA00"
      progName="COADM01C"
      title="Admin Menu"
      menu="admin"
      loadScreen={getAdminMenu}
    />
  );
}
