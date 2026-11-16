/**
 * COSTM01C — Statement View (S-21 OnlineStatements, CMD-7).
 *
 * Built against the CMD-4 contract GET /api/statements/{stmtId} (detail with
 * account/customer header fields, ErrorResponse `message` on a non-2xx) and the
 * CMD-4/CMD-5 /html and /pdf endpoints, with `fetch` stubbed.
 */
import React from 'react';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import StatementViewPage, {
  DISPLAY_MSG, NO_STMT_MSG, NOT_FOUND_MSG, toSummary,
} from './StatementViewPage';
import { renderScreen, stubBackend, currentUrl } from '../../testing/screenHarness';

const ACCT = '00000000001';
const DETAIL = {
  stmtId: 1003,
  acctId: 1,
  cardNum: '4111111111110002',
  cycle: '2026-10',
  txnCount: 7,
  totalAmount: 1234.5,
  generatedTs: '2026-11-01T02:15:30.123',
  custId: 1,
  firstName: 'Immanuel',
  middleName: 'Madeline',
  lastName: 'Kessler',
  currBal: 194,
  creditLimit: 2020,
};

function summaryValue(label) {
  const table = screen.getByRole('table', { name: 'Statement summary' });
  const header = within(table).getByRole('rowheader', { name: label });
  return header.closest('tr').querySelector('td').textContent;
}

async function openStatement(route = `/statements/view?stmtId=1003&acctId=${ACCT}`) {
  const utils = renderScreen(<StatementViewPage />, { route });
  await screen.findByRole('table', { name: 'Statement summary' });
  return utils;
}

describe('COSTM01C Statement View', () => {
  test('renders the summary header from GET /api/statements/{stmtId}', async () => {
    const backend = stubBackend();
    backend.get('/api/statements/1003', DETAIL);
    await openStatement();

    expect(screen.getByText('COSTM01C')).toBeInTheDocument();
    expect(screen.getByText('Statement View')).toBeInTheDocument();
    expect(backend.callsTo('GET', '/api/statements/1003')).toHaveLength(1);
    expect(summaryValue('Account :')).toEqual('1');
    expect(summaryValue('Customer :')).toEqual('1  Immanuel Madeline Kessler');
    expect(summaryValue('Card :')).toEqual('****0002');
    expect(summaryValue('Cycle :')).toEqual('2026-10');
    expect(summaryValue('Transactions :')).toEqual('7');
    expect(summaryValue('Total :')).toEqual('1,234.50');
    expect(summaryValue('Current Balance :')).toEqual('194.00');
    expect(summaryValue('Credit Limit :')).toEqual('2,020.00');
    expect(summaryValue('Generated :')).toEqual('2026-11-01 02:15:30');
    expect(screen.getByRole('status').textContent).toEqual(DISPLAY_MSG);
    expect(screen.getByRole('alert').textContent).toEqual('');
  });

  test('the stored HTML statement is shown in a fully sandboxed iframe on /html', async () => {
    const backend = stubBackend();
    backend.get('/api/statements/1003', DETAIL);
    await openStatement();

    const frame = screen.getByTitle('Statement preview');
    expect(frame.tagName).toEqual('IFRAME');
    expect(frame.getAttribute('src')).toEqual('/api/statements/1003/html');
    expect(frame.hasAttribute('sandbox')).toBe(true);
    expect(frame.getAttribute('sandbox')).toEqual('');
  });

  test('Download PDF / Download HTML link to the /pdf and /html endpoints', async () => {
    const backend = stubBackend();
    backend.get('/api/statements/1003', DETAIL);
    await openStatement();

    const pdf = screen.getByRole('link', { name: 'Download PDF' });
    const html = screen.getByRole('link', { name: 'Download HTML' });
    expect(pdf.getAttribute('href')).toEqual('/api/statements/1003/pdf');
    expect(html.getAttribute('href')).toEqual('/api/statements/1003/html');
    expect(pdf.hasAttribute('download')).toBe(true);
    expect(html.hasAttribute('download')).toBe(true);
  });

  test('F3 returns to the list with the searched account preserved', async () => {
    const backend = stubBackend();
    backend.get('/api/statements/1003', DETAIL);
    const { location } = await openStatement();

    fireEvent.keyDown(window, { key: 'F3' });
    await waitFor(() => expect(currentUrl(location)).toEqual(`/statements?acctId=${ACCT}`));
  });

  test('the F3=Back button does the same', async () => {
    const backend = stubBackend();
    backend.get('/api/statements/1003', DETAIL);
    const { location } = await openStatement();

    fireEvent.click(screen.getByRole('button', { name: 'F3=Back' }));
    await waitFor(() => expect(currentUrl(location)).toEqual(`/statements?acctId=${ACCT}`));
  });

  test('F3 without a searched account falls back to the statement\'s own account', async () => {
    const backend = stubBackend();
    backend.get('/api/statements/1003', { ...DETAIL, acctId: '00000000042' });
    const { location } = await openStatement('/statements/view?stmtId=1003');

    fireEvent.keyDown(window, { key: 'F3' });
    await waitFor(() => expect(currentUrl(location)).toEqual('/statements?acctId=00000000042'));
  });

  test('a statement that no longer exists shows the API message, no preview, no downloads', async () => {
    const backend = stubBackend();
    backend.get('/api/statements/9999', {
      status: 404,
      body: { message: 'Statement 9999 not found' },
    });
    const { location } = renderScreen(<StatementViewPage />, {
      route: `/statements/view?stmtId=9999&acctId=${ACCT}`,
    });

    await waitFor(() => expect(screen.getByRole('alert').textContent)
      .toEqual('Statement 9999 not found'));
    expect(screen.queryByTitle('Statement preview')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Download PDF' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Download HTML' })).not.toBeInTheDocument();

    fireEvent.keyDown(window, { key: 'F3' });
    await waitFor(() => expect(currentUrl(location)).toEqual(`/statements?acctId=${ACCT}`));
  });

  test('a 404 without a body falls back to the not-found message', async () => {
    const backend = stubBackend();
    backend.get('/api/statements/9999', { status: 404, body: null });
    renderScreen(<StatementViewPage />, { route: '/statements/view?stmtId=9999' });

    await waitFor(() => expect(screen.getByRole('alert').textContent).toEqual(NOT_FOUND_MSG));
    expect(screen.queryByTitle('Statement preview')).not.toBeInTheDocument();
  });

  test('no stmtId: prompts to go back and calls nothing', async () => {
    const backend = stubBackend();
    renderScreen(<StatementViewPage />, { route: '/statements/view' });

    await waitFor(() => expect(screen.getByRole('alert').textContent).toEqual(NO_STMT_MSG));
    await waitFor(() => expect(backend.callsTo('GET', '/api/auth/session')).toHaveLength(1));
    expect(backend.calls.filter((c) => c.url.startsWith('/api/statements'))).toHaveLength(0);
  });
});

describe('toSummary', () => {
  test('reads account/customer header fields nested under account / customer', () => {
    expect(toSummary({
      stmtId: 5,
      cycle: '2026-09',
      account: { acctId: '00000000001', cardNum: '4111111111111234', currBal: 10 },
      customer: { custId: 9, firstName: 'Ann', lastName: 'Lee' },
    })).toMatchObject({
      acctId: '00000000001',
      cardNum: '4111111111111234',
      currBal: 10,
      custId: 9,
      customerName: 'Ann Lee',
    });
  });
});
