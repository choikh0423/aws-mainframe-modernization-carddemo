/**
 * CT00 — List Transactions (COTRN00C, map COTRN0A).
 *
 * Oracle: `app/cbl/COTRN00C.cbl` (the `WS-MESSAGE` literals at :196-202,
 * :209-217, :248-249, :269-271, :608-609, :642-643, :676-677) and
 * `migration/transaction-management/docs/TransactionManagement_functional_requirement.md`
 * §CT00.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import TransactionListPage from './TransactionListPage';
import { renderScreen, stubBackend, currentUrl } from '../../testing/screenHarness';

// COTRN00C literals, verbatim.
const ALREADY_TOP = 'You are already at the top of the page...';
const ALREADY_BOTTOM = 'You are already at the bottom of the page...';
const REACHED_BOTTOM = 'You have reached the bottom of the page...';
const INVALID_SELECTION = 'Invalid selection. Valid value is S';
const TRAN_ID_NUMERIC = 'Tran ID must be Numeric ...';

const PAGE_SIZE = 10;

function tran(n) {
  const id = String(n).padStart(16, '0');
  return {
    id,
    date: '2022-06-01',
    description: `PURCHASE ${n}`,
    amountDisplay: '-000000100.00',
  };
}

function pageOf(from, count, { hasNextPage = true, hasPrevPage = false } = {}) {
  const rows = Array.from({ length: count }, (unused, i) => tran(from + i));
  return {
    rows,
    count,
    firstId: rows.length ? rows[0].id : null,
    lastId: rows.length ? rows[rows.length - 1].id : null,
    hasNextPage,
    hasPrevPage,
  };
}

const PAGE_1 = pageOf(1, PAGE_SIZE, { hasNextPage: true, hasPrevPage: false });
const PAGE_2 = pageOf(11, PAGE_SIZE, { hasNextPage: false, hasPrevPage: true });

function message() {
  return screen.getByRole('alert').textContent;
}

async function renderList(backend) {
  const view = renderScreen(<TransactionListPage />);
  await screen.findByText('0000000000000001');
  expect(backend.callsTo('GET', '/api/transactions')).toHaveLength(1);
  return view;
}

describe('CT00 List Transactions — paging (FR-L1, FR-L3, FR-L4)', () => {
  test('FR-L1: the first entry browses from the top of TRANSACT, ten lines', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', PAGE_1);

    await renderList(backend);

    expect(backend.callsTo('GET', '/api/transactions')[0].url).toEqual('/api/transactions');
    expect(screen.getAllByRole('row')).toHaveLength(PAGE_SIZE + 1); // header + 10
  });

  test('FR-L3: PF8 browses forward from the last Tran ID shown', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', [PAGE_1, PAGE_2]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await screen.findByText('0000000000000011');
    const requests = backend.callsTo('GET', '/api/transactions');
    expect(requests).toHaveLength(2);
    expect(requests[1].url).toEqual('/api/transactions?startId=0000000000000010&dir=next');
  });

  test('FR-L4: PF7 browses back from the first Tran ID shown', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', [PAGE_1, PAGE_2, PAGE_1]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });
    await screen.findByText('0000000000000011');
    fireEvent.keyDown(window, { key: 'F7' });

    await screen.findByText('0000000000000001');
    const requests = backend.callsTo('GET', '/api/transactions');
    expect(requests).toHaveLength(3);
    expect(requests[2].url).toEqual('/api/transactions?startId=0000000000000011&dir=prev');
  });

  test('FR-L4: PF7 on page 1 keeps the page and shows the PF7 guard literal', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', PAGE_1);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F7' });

    await waitFor(() => expect(message()).toEqual(ALREADY_TOP));
    expect(backend.callsTo('GET', '/api/transactions')).toHaveLength(1);
  });

  /**
   * The two bottom-of-file literals are distinct events in COTRN00C and must
   * not be swapped: PROCESS-PF8-KEY's NEXT-PAGE guard (`:269-271`) is the
   * mirror image of the PF7 guard two paragraphs above, while `:642-643` is
   * READNEXT hitting ENDFILE while *filling* a page.
   */
  test('FR-L3: PF8 with no next page shows the PF8 guard literal', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', pageOf(1, PAGE_SIZE, { hasNextPage: false }));

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await waitFor(() => expect(message()).toEqual(ALREADY_BOTTOM));
    expect(backend.callsTo('GET', '/api/transactions')).toHaveLength(1);
  });

  test('FR-L3: a browse that ends mid-page shows the ENDFILE literal instead', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', [PAGE_1, pageOf(11, 4, { hasNextPage: false, hasPrevPage: true })]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await waitFor(() => expect(message()).toEqual(REACHED_BOTTOM));
  });

  test('FR-L3: a full page carries no bottom-of-file message', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', PAGE_1);

    await renderList(backend);

    expect(message()).toEqual('');
  });

  test('FR-L4: paging back never shows a bottom-of-file message', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', [
      PAGE_1,
      pageOf(11, 4, { hasNextPage: false, hasPrevPage: true }),
      pageOf(1, 4, { hasNextPage: true, hasPrevPage: false }),
    ]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });
    await waitFor(() => expect(message()).toEqual(REACHED_BOTTOM));
    fireEvent.keyDown(window, { key: 'F7' });

    await waitFor(() => expect(message()).toEqual(''));
  });
});

describe('CT00 List Transactions — selection flag (FR-L5, FR-L6)', () => {
  test('FR-L5: `S` opens the row in CT01', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', PAGE_1);

    const view = await renderList(backend);
    fireEvent.change(screen.getByLabelText('select 0000000000000004'), { target: { value: 'S' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Filter / Select' }));

    await waitFor(() => expect(currentUrl(view.location))
      .toEqual('/transactions/view?id=0000000000000004'));
  });

  test('FR-L5: the flag is accepted in either case (`WHEN \'S\' WHEN \'s\'`)', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', PAGE_1);

    const view = await renderList(backend);
    fireEvent.change(screen.getByLabelText('select 0000000000000002'), { target: { value: 's' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Filter / Select' }));

    await waitFor(() => expect(currentUrl(view.location))
      .toEqual('/transactions/view?id=0000000000000002'));
  });

  test('FR-L6: any other flag is refused verbatim and the page stays put', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', PAGE_1);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('select 0000000000000003'), { target: { value: 'X' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Filter / Select' }));

    await waitFor(() => expect(message()).toEqual(INVALID_SELECTION));
    expect(backend.callsTo('GET', '/api/transactions')).toHaveLength(1);
  });
});

describe('CT00 List Transactions — filter and keys (FR-L2, FR-L7, FR-L8)', () => {
  test('FR-L2: ENTER with no flag restarts the browse at the entered Tran ID', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', PAGE_1);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('Search Tran ID:'), { target: { value: '0000000000000007' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Filter / Select' }));

    await waitFor(() => expect(backend.callsTo('GET', '/api/transactions')).toHaveLength(2));
    expect(backend.callsTo('GET', '/api/transactions')[1].url)
      .toEqual('/api/transactions?startId=0000000000000007');
  });

  test('FR-L7: a non-numeric Tran ID filter shows the numeric literal', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', [PAGE_1, { status: 400, body: { message: TRAN_ID_NUMERIC } }]);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('Search Tran ID:'), { target: { value: 'ABC' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Filter / Select' }));

    await waitFor(() => expect(message()).toEqual(TRAN_ID_NUMERIC));
  });

  test('FR-L8: PF3 returns to the main menu', async () => {
    const backend = stubBackend();
    backend.get('/api/transactions', PAGE_1);

    const view = await renderList(backend);
    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/menu'));
  });
});
