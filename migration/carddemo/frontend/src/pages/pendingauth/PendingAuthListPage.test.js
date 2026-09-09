/**
 * CPVS — View Authorizations (COPAUS0C, map COPAU0A).
 *
 * Oracle: `app/cbl/COPAUS0C.cbl` and
 * `docs/migration/streams/PendingAuthorizations/programs/COPAUS0C_functional_requirement.md`
 * (FR-PA-S02…FR-PA-S16 and its "Messages (verbatim)" block). Five rows per page,
 * `S`/`s` selects, and the boundary literals are the same two the other browses
 * use — assert them character for character.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import PendingAuthListPage from './PendingAuthListPage';
import { renderScreen, stubBackend, currentUrl } from '../../testing/screenHarness';

// COPAUS0C messages, verbatim (note the space before `...` on the numeric one).
const PLEASE_ENTER_ACCT = 'Please enter Acct Id...';
const ACCT_NUMERIC = 'Acct Id must be Numeric ...';
const INVALID_SELECTION = 'Invalid selection. Valid value is S';
const ALREADY_TOP = 'You are already at the top of the page...';
const ALREADY_BOTTOM = 'You are already at the bottom of the page...';

const ROWS_PER_PAGE = 5;
const ACCT_ID = '00000000011';

function auth(n) {
  return {
    authKey: `KEY${String(n).padStart(3, '0')}`,
    transactionId: `TRAN${String(n).padStart(4, '0')}`,
    date: '2022-06-01',
    time: '10.11.12',
    authType: '01',
    approvalStatus: 'A',
    matchStatus: 'N',
    amountDisplay: '100.00',
  };
}

function pageOf(from, pageNum, { nextPage = true, message = '' } = {}) {
  const rows = Array.from({ length: ROWS_PER_PAGE }, (unused, i) => auth(from + i));
  return {
    acctId: ACCT_ID,
    custId: '000000011',
    customerName: 'JOHN DOE',
    addressLine1: '1 MAIN ST',
    addressLine2: 'ANYTOWN',
    phone: '(555)555-5555',
    creditLimit: '5000.00',
    cashLimit: '1000.00',
    creditBalance: '250.00',
    cashBalance: '0.00',
    approvedCount: '5',
    declinedCount: '0',
    approvedAmount: '500.00',
    declinedAmount: '0.00',
    rows,
    pageNum,
    firstKey: rows[0].authKey,
    lastKey: rows[rows.length - 1].authKey,
    nextPage,
    message,
  };
}

const PAGE_1 = pageOf(1, 1);
const PAGE_2 = pageOf(6, 2, { nextPage: false });

function message() {
  return screen.getByRole('alert').textContent;
}

async function renderWithAccount(backend) {
  const view = renderScreen(<PendingAuthListPage />);
  fireEvent.change(screen.getByLabelText('Search Acct Id:'), { target: { value: ACCT_ID } });
  fireEvent.click(screen.getByRole('button', { name: 'ENTER=Continue' }));
  await screen.findByText('TRAN0001');
  return view;
}

describe('CPVS View Authorizations — account key (FR-PA-S02, FR-PA-S03)', () => {
  test('FR-PA-S02: a blank account id is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get('/api/pending-authorizations', { ...PAGE_1, rows: [], message: PLEASE_ENTER_ACCT });

    renderScreen(<PendingAuthListPage />);
    fireEvent.click(screen.getByRole('button', { name: 'ENTER=Continue' }));

    await waitFor(() => expect(message()).toEqual(PLEASE_ENTER_ACCT));
    expect(backend.callsTo('GET', '/api/pending-authorizations')[0].url)
      .toEqual('/api/pending-authorizations');
  });

  test('FR-PA-S03: a non-numeric account id is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get('/api/pending-authorizations', { ...PAGE_1, rows: [], message: ACCT_NUMERIC });

    renderScreen(<PendingAuthListPage />);
    fireEvent.change(screen.getByLabelText('Search Acct Id:'), { target: { value: 'ABCDEFGHIJK' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER=Continue' }));

    await waitFor(() => expect(message()).toEqual(ACCT_NUMERIC));
    expect(backend.callsTo('GET', '/api/pending-authorizations')[0].url)
      .toEqual('/api/pending-authorizations?acctId=ABCDEFGHIJK');
  });
});

describe('CPVS View Authorizations — paging (FR-PA-S08…FR-PA-S11)', () => {
  test('FR-PA-S08: a page holds five authorizations', async () => {
    const backend = stubBackend();
    backend.get('/api/pending-authorizations', PAGE_1);

    await renderWithAccount(backend);

    expect(screen.getAllByRole('row')).toHaveLength(ROWS_PER_PAGE + 1); // header + 5
  });

  test('FR-PA-S10: PF8 repositions on the last key of the page', async () => {
    const backend = stubBackend();
    backend.get('/api/pending-authorizations', [PAGE_1, PAGE_2]);

    await renderWithAccount(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await screen.findByText('TRAN0006');
    const requests = backend.callsTo('GET', '/api/pending-authorizations');
    expect(requests).toHaveLength(2);
    expect(requests[1].url).toEqual(
      `/api/pending-authorizations?acctId=${ACCT_ID}&dir=next&startKey=KEY005&pageNum=1`,
    );
  });

  test('FR-PA-S11: PF7 repositions on the first key of the page', async () => {
    const backend = stubBackend();
    backend.get('/api/pending-authorizations', [PAGE_1, PAGE_2, PAGE_1]);

    await renderWithAccount(backend);
    fireEvent.keyDown(window, { key: 'F8' });
    await screen.findByText('TRAN0006');
    fireEvent.keyDown(window, { key: 'F7' });

    await screen.findByText('TRAN0001');
    const requests = backend.callsTo('GET', '/api/pending-authorizations');
    expect(requests).toHaveLength(3);
    expect(requests[2].url).toEqual(
      `/api/pending-authorizations?acctId=${ACCT_ID}&dir=prev&startKey=KEY006&pageNum=2`,
    );
  });

  test('FR-PA-S10: the bottom boundary literal is shown verbatim', async () => {
    const backend = stubBackend();
    backend.get('/api/pending-authorizations', [
      PAGE_1,
      { ...PAGE_1, nextPage: false, message: ALREADY_BOTTOM },
    ]);

    await renderWithAccount(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await waitFor(() => expect(message()).toEqual(ALREADY_BOTTOM));
  });

  test('FR-PA-S11: the top boundary literal is shown verbatim', async () => {
    const backend = stubBackend();
    backend.get('/api/pending-authorizations', [PAGE_1, { ...PAGE_1, message: ALREADY_TOP }]);

    await renderWithAccount(backend);
    fireEvent.keyDown(window, { key: 'F7' });

    await waitFor(() => expect(message()).toEqual(ALREADY_TOP));
  });
});

describe('CPVS View Authorizations — selection flags (FR-PA-S12…FR-PA-S14)', () => {
  test('FR-PA-S12: `S` posts the five flags in row order and opens CPVD', async () => {
    const backend = stubBackend();
    backend.get('/api/pending-authorizations', PAGE_1);
    backend.post('/api/pending-authorizations/selection', {
      selected: true,
      nextProgram: 'COPAUS1C',
      authKey: 'KEY003',
      message: '',
    });

    const view = await renderWithAccount(backend);
    fireEvent.change(screen.getByLabelText('select TRAN0003'), { target: { value: 'S' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER=Continue' }));

    await waitFor(() => expect(currentUrl(view.location))
      .toEqual(`/pending-authorizations/detail?acctId=${ACCT_ID}&authKey=KEY003`));
    const selection = backend.callsTo('POST', '/api/pending-authorizations/selection')[0].body;
    expect(selection.acctId).toEqual(ACCT_ID);
    expect(selection.rows).toEqual([
      { flag: '', authKey: 'KEY001' },
      { flag: '', authKey: 'KEY002' },
      { flag: 'S', authKey: 'KEY003' },
      { flag: '', authKey: 'KEY004' },
      { flag: '', authKey: 'KEY005' },
    ]);
  });

  test('FR-PA-S12: a lower-case `s` selects as well', async () => {
    const backend = stubBackend();
    backend.get('/api/pending-authorizations', PAGE_1);
    backend.post('/api/pending-authorizations/selection', {
      selected: true,
      authKey: 'KEY001',
      message: '',
    });

    const view = await renderWithAccount(backend);
    fireEvent.change(screen.getByLabelText('select TRAN0001'), { target: { value: 's' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER=Continue' }));

    await waitFor(() => expect(currentUrl(view.location))
      .toEqual(`/pending-authorizations/detail?acctId=${ACCT_ID}&authKey=KEY001`));
  });

  test('FR-PA-S13: any other flag is refused verbatim and the page stays put', async () => {
    const backend = stubBackend();
    backend.get('/api/pending-authorizations', PAGE_1);
    backend.post('/api/pending-authorizations/selection', {
      selected: false,
      message: INVALID_SELECTION,
    });

    const view = await renderWithAccount(backend);
    fireEvent.change(screen.getByLabelText('select TRAN0002'), { target: { value: 'X' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER=Continue' }));

    await waitFor(() => expect(message()).toEqual(INVALID_SELECTION));
    expect(view.location.pathname).toEqual('/');
  });
});

describe('CPVS View Authorizations — keys (FR-PA-S16)', () => {
  test('FR-PA-S16: PF3 returns to the main menu', async () => {
    const backend = stubBackend();
    backend.get('/api/pending-authorizations', PAGE_1);

    const view = await renderWithAccount(backend);
    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/menu'));
  });
});
