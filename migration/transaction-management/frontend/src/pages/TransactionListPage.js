import React from 'react';
import Layout from '../components/Layout';

/**
 * CT00 — Transaction List (COTRN00C). Wave A stub; the paged 10/screen list,
 * PF7/PF8 paging and row-select land in Wave C.
 */
export default function TransactionListPage() {
  return (
    <Layout tranId="CT00" progName="COTRN00C" title="List Transactions">
      <p>Transaction list (CT00) — coming in Wave C.</p>
    </Layout>
  );
}
