import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import TransactionListPage from './pages/TransactionListPage';
import TransactionViewPage from './pages/TransactionViewPage';
import AddTransactionPage from './pages/AddTransactionPage';

/**
 * Transaction Management routing shell (Wave A).
 * Routes are wired now; the screens themselves are stubs until later waves:
 *   /transactions      -> CT00 List  (COTRN00C)
 *   /transactions/view -> CT01 View  (COTRN01C)
 *   /transactions/add  -> CT02 Add   (COTRN02C)
 */
function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/transactions" replace />} />
      <Route path="/transactions" element={<TransactionListPage />} />
      <Route path="/transactions/view" element={<TransactionViewPage />} />
      <Route path="/transactions/add" element={<AddTransactionPage />} />
      <Route path="*" element={<Navigate to="/transactions" replace />} />
    </Routes>
  );
}

export default App;
