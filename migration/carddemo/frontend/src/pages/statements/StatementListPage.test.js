/**
 * COSTM00C — Statement List (S-21 OnlineStatements, CMD-6).
 *
 * Built against the CMD-4 contract GET /api/statements?acctId=&page= (pages of
 * 10, newest cycle first, ErrorResponse `message` on a non-2xx) with `fetch`
 * stubbed, so the exact request the screen sends stays under assertion.
 */
import React from 'react';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import StatementListPage, {
  ALREADY_BOTTOM_MSG,
  ALREADY_TOP_MSG,
  EMPTY_MSG,
  INVALID_SEL_MSG,
  PROMPT_MSG,
} from './StatementListPage';
import { renderScreen, stubBackend, currentUrl } from '../../testing/screenHarness';

const ACCT = '00000000001';
const BAD_ACCT_MSG = 'Account Filter must  be a non-zero 11 digit number';
const UNKNOWN_ACCT_MSG = 'Did not find this account in account master file';

// Newest cycle first: stmt(1) is 2026-12, stmt(12) is 2026-01.
function stmt(n) {
  return {
    stmtId: 1000 + n,
    cycle: `2026-${String(13 - n).padStart(2, '0')}`,
    cardNum: '****0002',
    txnCount: n,
    totalAmount: 1234.5 + n,
    generatedTs: `2026-11-01T02:15:${String(n).padStart(2, '0')}.123`,
  };
}

const PAGE_0 = {
  statements: Array.from({ length: 10 }, (unused, i) => stmt(i + 1)),
  page: 0,
  totalPages: 2,
  hasNext: true,
};
const PAGE_1 = {
  statements: [stmt(11), stmt(12)],
  page: 1,
  totalPages: 2,
  hasNext: false,
};

function message() {
  return screen.getByRole('alert').textContent;
}

function info() {
  return screen.getByRole('status').textContent;
}

function dataRows() {
  return screen.getAllByRole('row').slice(1);
}

async function search(acctId = ACCT) {
  fireEvent.change(screen.getByLabelText('Account Number :'), { target: { value: acctId } });
  fireEvent.click(screen.getByRole('button', { name: 'ENTER' }));
}

describe('COSTM00C Statement List', () => {
  test('opens with the prompt, no table and no API call', async () => {
    const backend = stubBackend();
    renderScreen(<StatementListPage />, { route: '/statements' });

    expect(screen.getByText('COSTM00C')).toBeInTheDocument();
    expect(screen.getByText('Statement List')).toBeInTheDocument();
    expect(info()).toEqual(PROMPT_MSG);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    await waitFor(() => expect(backend.callsTo('GET', '/api/auth/session')).toHaveLength(1));
    expect(backend.callsTo('GET', '/api/statements')).toHaveLength(0);
  });

  test('ENTER lists the cycles: Cycle, Card ****1234, Transactions, Total, Generated', async () => {
    const backend = stubBackend();
    backend.get('/api/statements', PAGE_0);
    renderScreen(<StatementListPage />);

    await search();

    await screen.findByText('2026-12');
    expect(backend.callsTo('GET', '/api/statements').map((c) => c.url))
      .toEqual([`/api/statements?acctId=${ACCT}&page=0`]);
    const headers = screen.getAllByRole('columnheader').map((h) => h.textContent);
    expect(headers).toEqual(['Sel', 'Cycle', 'Card', 'Transactions', 'Total', 'Generated']);
    expect(dataRows()).toHaveLength(10);
    const cells = within(dataRows()[0]).getAllByRole('cell').map((c) => c.textContent);
    expect(cells.slice(1)).toEqual(['2026-12', '****0002', '1', '1,235.50', '2026-11-01 02:15:01']);
    expect(screen.getByText('Page 1')).toBeInTheDocument();
    expect(message()).toEqual('');
    expect(info()).toEqual('');
  });

  test('an unmasked card number is masked to the last four digits', async () => {
    const backend = stubBackend();
    backend.get('/api/statements', { statements: [{ ...stmt(1), cardNum: '4111111111111234' }], page: 0, hasNext: false });
    renderScreen(<StatementListPage />);

    await search();

    expect(await screen.findByText('****1234')).toBeInTheDocument();
    expect(screen.queryByText('4111111111111234')).not.toBeInTheDocument();
  });

  test('also reads a Spring Page body (content / number / last)', async () => {
    const backend = stubBackend();
    backend.get('/api/statements', { content: [stmt(1), stmt(2)], number: 0, totalPages: 1, last: true });
    renderScreen(<StatementListPage />);

    await search();

    await screen.findByText('2026-12');
    expect(dataRows()).toHaveLength(2);
  });

  test('F8 / F7 page through the cycles with page=1 / page=0', async () => {
    const backend = stubBackend();
    backend.get('/api/statements', (call) => (call.url.endsWith('page=1') ? PAGE_1 : PAGE_0));
    renderScreen(<StatementListPage />);

    await search();
    await screen.findByText('Page 1');

    fireEvent.keyDown(window, { key: 'F8' });
    await screen.findByText('Page 2');
    expect(dataRows()).toHaveLength(2);
    expect(screen.getByText('2026-01')).toBeInTheDocument();

    fireEvent.keyDown(window, { key: 'F7' });
    await screen.findByText('Page 1');
    expect(dataRows()).toHaveLength(10);

    expect(backend.callsTo('GET', '/api/statements').map((c) => c.url)).toEqual([
      `/api/statements?acctId=${ACCT}&page=0`,
      `/api/statements?acctId=${ACCT}&page=1`,
      `/api/statements?acctId=${ACCT}&page=0`,
    ]);
  });

  test('F7 on the first page and F8 on the last page stay put with the boundary messages', async () => {
    const backend = stubBackend();
    backend.get('/api/statements', (call) => (call.url.endsWith('page=1') ? PAGE_1 : PAGE_0));
    renderScreen(<StatementListPage />);

    await search();
    await screen.findByText('Page 1');
    fireEvent.click(screen.getByRole('button', { name: 'F7=Backward' }));
    expect(message()).toEqual(ALREADY_TOP_MSG);

    fireEvent.click(screen.getByRole('button', { name: 'F8=Forward' }));
    await screen.findByText('Page 2');
    fireEvent.keyDown(window, { key: 'F8' });
    expect(message()).toEqual(ALREADY_BOTTOM_MSG);
    expect(screen.getByText('Page 2')).toBeInTheDocument();
    expect(backend.callsTo('GET', '/api/statements')).toHaveLength(2);
  });

  test('a 400 validation error from the API is shown verbatim and clears the table', async () => {
    const backend = stubBackend();
    backend.get('/api/statements', (call) => (call.url.includes('acctId=ABC')
      ? { status: 400, body: { status: 400, error: 'Bad Request', message: BAD_ACCT_MSG } }
      : PAGE_0));
    renderScreen(<StatementListPage />);

    await search();
    await screen.findByText('Page 1');
    await search('ABC');

    await waitFor(() => expect(message()).toEqual(BAD_ACCT_MSG));
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(backend.callsTo('GET', '/api/statements')[1].url)
      .toEqual('/api/statements?acctId=ABC&page=0');
  });

  test('an unknown account (404) shows the API message verbatim', async () => {
    const backend = stubBackend();
    backend.get('/api/statements', { status: 404, body: { status: 404, message: UNKNOWN_ACCT_MSG } });
    renderScreen(<StatementListPage />);

    await search('99999999999');

    await waitFor(() => expect(message()).toEqual(UNKNOWN_ACCT_MSG));
  });

  test('no statements -> "No statements found for this account"', async () => {
    const backend = stubBackend();
    backend.get('/api/statements', { statements: [], page: 0, totalPages: 0, hasNext: false });
    renderScreen(<StatementListPage />);

    await search();

    await waitFor(() => expect(info()).toEqual(EMPTY_MSG));
    expect(screen.getByText('No statements found for this account')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(message()).toEqual('');
  });

  test('S beside a row + ENTER opens that statement in the Statement View route', async () => {
    const backend = stubBackend();
    backend.get('/api/statements', PAGE_0);
    const { location } = renderScreen(<StatementListPage />, { route: '/statements' });

    await search();
    await screen.findByText('Page 1');
    fireEvent.change(screen.getByLabelText('select statement 2026-10'), { target: { value: 's' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER' }));

    await waitFor(() => expect(currentUrl(location))
      .toEqual(`/statements/view?stmtId=1003&acctId=${ACCT}`));
    expect(backend.callsTo('GET', '/api/statements')).toHaveLength(1);
  });

  test('any other selection flag -> "Invalid selection. Valid value is S"', async () => {
    const backend = stubBackend();
    backend.get('/api/statements', PAGE_0);
    const { location } = renderScreen(<StatementListPage />, { route: '/statements' });

    await search();
    await screen.findByText('Page 1');
    fireEvent.change(screen.getByLabelText('select statement 2026-12'), { target: { value: 'X' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER' }));

    expect(message()).toEqual(INVALID_SEL_MSG);
    expect(location.pathname).toEqual('/statements');
    expect(backend.callsTo('GET', '/api/statements')).toHaveLength(1);
  });

  test('?acctId= lists that account without an ENTER (return from Statement View)', async () => {
    const backend = stubBackend();
    backend.get('/api/statements', PAGE_0);
    renderScreen(<StatementListPage />, { route: `/statements?acctId=${ACCT}` });

    await screen.findByText('Page 1');
    expect(screen.getByLabelText('Account Number :')).toHaveValue(ACCT);
    expect(backend.callsTo('GET', '/api/statements').map((c) => c.url))
      .toEqual([`/api/statements?acctId=${ACCT}&page=0`]);
  });

  test('F3 goes back to the Main Menu', async () => {
    stubBackend();
    const { location } = renderScreen(<StatementListPage />, { route: '/statements' });

    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(location.pathname).toEqual('/menu'));
  });
});
