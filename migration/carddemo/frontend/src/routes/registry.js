import React from 'react';
import SignOnPage from '../pages/SignOnPage';
import MainMenuPage from '../pages/MainMenuPage';
import AdminMenuPage from '../pages/AdminMenuPage';
import AccountViewPage from '../pages/account/AccountViewPage';
import AccountUpdatePage from '../pages/account/AccountUpdatePage';
import CardListPage from '../pages/card/CardListPage';
import CardDetailPage from '../pages/card/CardDetailPage';
import CardUpdatePage from '../pages/card/CardUpdatePage';
import TransactionListPage from '../pages/transaction/TransactionListPage';
import TransactionViewPage from '../pages/transaction/TransactionViewPage';
import AddTransactionPage from '../pages/transaction/AddTransactionPage';
import BillPaymentPage from '../pages/billpayment/BillPaymentPage';
import UserListPage from '../pages/user/UserListPage';
import AddUserPage from '../pages/user/AddUserPage';
import UpdateUserPage from '../pages/user/UpdateUserPage';
import DeleteUserPage from '../pages/user/DeleteUserPage';
import TransactionReportsPage from '../pages/reporting/TransactionReportsPage';
import PendingAuthListPage from '../pages/pendingauth/PendingAuthListPage';
import PendingAuthDetailPage from '../pages/pendingauth/PendingAuthDetailPage';
import TranTypeListPage from '../pages/trantype/TranTypeListPage';
import TranTypeUpdatePage from '../pages/trantype/TranTypeUpdatePage';

/**
 * The route registry: one entry per legacy online screen, in menu order.
 *
 * A stream session migrating a screen adds its page component under
 * `src/pages/<stream>/` and edits exactly one line here — the entry's
 * `element` — so two streams never touch the same lines. Entries without an
 * `element` render the shared placeholder, which keeps both menus navigable
 * end to end before every screen is migrated.
 *
 * Fields:
 *   program  the COBOL program name, as the menus and COMMAREA carry it
 *   tranId   the transaction id the screen shows in its header (WS-TRANID);
 *            screens reached by XCTL from a menu keep the menu's id, exactly
 *            as the legacy programs do
 *   path     the React Router path; menus navigate by program, not by path
 *   title    the screen title exactly as its BMS map shows it
 *   stream   the migration stream that owns the screen (see the README)
 *   element  the migrated screen, or null while it is still a placeholder
 */
export const SCREENS = [
  {
    program: 'COSGN00C',
    tranId: 'CC00',
    path: '/',
    title: 'Sign On',
    stream: 'S-01 AuthAndShell',
    element: <SignOnPage />,
  },
  {
    program: 'COMEN01C',
    tranId: 'CM00',
    path: '/menu',
    title: 'Main Menu',
    stream: 'S-01 AuthAndShell',
    element: <MainMenuPage />,
  },
  {
    program: 'COADM01C',
    tranId: 'CA00',
    path: '/admin',
    title: 'Admin Menu',
    stream: 'S-01 AuthAndShell',
    element: <AdminMenuPage />,
  },
  {
    program: 'COACTVWC',
    tranId: 'CM00',
    path: '/accounts/view',
    title: 'View Account',
    stream: 'S-02 AccountManagement',
    element: <AccountViewPage />,
  },
  {
    program: 'COACTUPC',
    tranId: 'CM00',
    path: '/accounts/update',
    title: 'Update Account',
    stream: 'S-02 AccountManagement',
    element: <AccountUpdatePage />,
  },
  {
    program: 'COCRDLIC',
    tranId: 'CM00',
    path: '/cards',
    title: 'List Credit Cards',
    stream: 'S-03 CardManagement',
    element: <CardListPage />,
  },
  {
    program: 'COCRDSLC',
    tranId: 'CM00',
    path: '/cards/view',
    title: 'View Credit Card Detail',
    stream: 'S-03 CardManagement',
    element: <CardDetailPage />,
  },
  {
    program: 'COCRDUPC',
    tranId: 'CM00',
    path: '/cards/update',
    title: 'Update Credit Card Details',
    stream: 'S-03 CardManagement',
    element: <CardUpdatePage />,
  },
  {
    program: 'COTRN00C',
    tranId: 'CT00',
    path: '/transactions',
    title: 'List Transactions',
    stream: 'S-04 TransactionManagement',
    element: <TransactionListPage />,
  },
  {
    program: 'COTRN01C',
    tranId: 'CT01',
    path: '/transactions/view',
    title: 'View Transaction',
    stream: 'S-04 TransactionManagement',
    element: <TransactionViewPage />,
  },
  {
    program: 'COTRN02C',
    tranId: 'CT02',
    path: '/transactions/add',
    title: 'Add Transaction',
    stream: 'S-04 TransactionManagement',
    element: <AddTransactionPage />,
  },
  {
    program: 'CORPT00C',
    tranId: 'CR00',
    path: '/reports',
    title: 'Transaction Reports',
    stream: 'S-07 Reporting',
    element: <TransactionReportsPage />,
  },
  {
    program: 'COBIL00C',
    tranId: 'CB00',
    path: '/bill-payment',
    title: 'Bill Payment',
    stream: 'S-06 BillPayment',
    element: <BillPaymentPage />,
  },
  {
    program: 'COPAUS0C',
    tranId: 'CPVS',
    path: '/pending-authorizations',
    title: 'View Authorizations',
    stream: 'S-09 PendingAuthorizations',
    element: <PendingAuthListPage />,
  },
  {
    program: 'COPAUS1C',
    tranId: 'CPVD',
    path: '/pending-authorizations/detail',
    title: 'View Authorization Details',
    stream: 'S-09 PendingAuthorizations',
    element: <PendingAuthDetailPage />,
  },
  {
    program: 'COUSR00C',
    tranId: 'CU00',
    path: '/admin/users',
    title: 'List Users',
    stream: 'S-05 UserManagement',
    element: <UserListPage />,
  },
  {
    program: 'COUSR01C',
    tranId: 'CU01',
    path: '/admin/users/add',
    title: 'Add User',
    stream: 'S-05 UserManagement',
    element: <AddUserPage />,
  },
  {
    program: 'COUSR02C',
    tranId: 'CU02',
    path: '/admin/users/update',
    title: 'Update User',
    stream: 'S-05 UserManagement',
    element: <UpdateUserPage />,
  },
  {
    program: 'COUSR03C',
    tranId: 'CU03',
    path: '/admin/users/delete',
    title: 'Delete User',
    stream: 'S-05 UserManagement',
    element: <DeleteUserPage />,
  },
  {
    program: 'COTRTLIC',
    tranId: 'CA00',
    path: '/admin/transaction-types',
    title: 'Maintain Transaction Type',
    stream: 'S-08 TransactionTypeManagement',
    element: <TranTypeListPage />,
  },
  {
    program: 'COTRTUPC',
    tranId: 'CA00',
    path: '/admin/transaction-types/update',
    title: 'Maintain Transaction Type',
    stream: 'S-08 TransactionTypeManagement',
    element: <TranTypeUpdatePage />,
  },
];

/** The registry entry for a COBOL program name, or undefined if unknown. */
export function screenForProgram(program) {
  return SCREENS.find((screen) => screen.program === program);
}

/**
 * Where a menu selection navigates. The menus hand back the COMMAREA program
 * name (CDEMO-TO-PROGRAM), which the shell turns into a route here.
 */
export function pathForProgram(program) {
  const screen = screenForProgram(program);
  return screen ? screen.path : null;
}
