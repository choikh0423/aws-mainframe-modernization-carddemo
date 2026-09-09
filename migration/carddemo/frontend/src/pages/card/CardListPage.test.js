/**
 * CCLI — List Credit Cards (COCRDLIC, map COCRDLI).
 *
 * Oracle: `app/cbl/COCRDLIC.cbl`, `app/bms/COCRDLI.bms` and
 * `docs/migration/streams/CardManagement/programs/COCRDLIC_functional_requirement.md`
 * (L-01…L-21 and §4 "Messages (verbatim)"). The map has seven detail lines, the
 * paging state travels with every turn, and the message literals are upper case
 * exactly as the COBOL moves them into `WS-RETURN-MSG`.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import CardListPage from './CardListPage';
import { renderScreen, stubBackend, currentUrl } from '../../testing/screenHarness';

// COCRDLIC.cbl:901-912, 1075-1121, 1382-1405 — verbatim.
const NO_MORE_PAGES = 'NO MORE PAGES TO DISPLAY';
const NO_PREVIOUS_PAGES = 'NO PREVIOUS PAGES TO DISPLAY';
const ONLY_ONE_RECORD = 'PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE';
const INVALID_ACTION_CODE = 'INVALID ACTION CODE';
const ACCOUNT_FILTER_MSG = 'ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER';
const CARD_FILTER_MSG = 'CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER';

const ROWS_PER_PAGE = 7;

function card(n) {
  return {
    acctId: '00000000011',
    cardNum: `4000000000000${String(n).padStart(3, '0')}`,
    activeStatus: 'Y',
  };
}

function pageOf(pageNumber, from, { nextPageExists = true, lastPageShown = false, errorMessage = '' } = {}) {
  const rows = Array.from({ length: ROWS_PER_PAGE }, (unused, i) => card(from + i));
  return {
    rows,
    pageNumber,
    firstCardNum: rows[0].cardNum,
    lastCardNum: rows[rows.length - 1].cardNum,
    nextPageExists,
    lastPageShown,
    infoMessage: '',
    errorMessage,
  };
}

const PAGE_1 = pageOf(1, 1);
const PAGE_2 = pageOf(2, 8, { nextPageExists: false, lastPageShown: true });

function message() {
  return screen.getByRole('alert').textContent;
}

async function renderList(backend) {
  const view = renderScreen(<CardListPage />);
  await screen.findByText('4000000000000001');
  expect(backend.callsTo('GET', '/api/cards')).toHaveLength(1);
  return view;
}

describe('CCLI List Credit Cards — paging (L-06…L-11)', () => {
  test('L-01/L-06: the first turn browses from the top with no filters, seven lines', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', PAGE_1);

    await renderList(backend);

    expect(backend.callsTo('GET', '/api/cards')[0].url)
      .toEqual('/api/cards?pageNumber=1&nextPageExists=false&lastPageShown=false');
    expect(screen.getAllByRole('row')).toHaveLength(ROWS_PER_PAGE + 1); // header + 7
    expect(screen.getByText('Page 1')).toBeInTheDocument();
  });

  test('L-08: PF8 sends dir=F and the paging state of the page on show', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', [PAGE_1, PAGE_2]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await screen.findByText('Page 2');
    const requests = backend.callsTo('GET', '/api/cards');
    expect(requests).toHaveLength(2);
    expect(requests[1].url).toEqual(
      '/api/cards?dir=F&pageNumber=1&firstCardNum=4000000000000001'
      + '&lastCardNum=4000000000000007&nextPageExists=true&lastPageShown=false',
    );
  });

  test('L-09: past the last page the map keeps the rows and shows the literal', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', [
      PAGE_1,
      { ...PAGE_1, lastPageShown: true, nextPageExists: false, errorMessage: NO_MORE_PAGES },
    ]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await waitFor(() => expect(message()).toEqual(NO_MORE_PAGES));
    expect(screen.getByText('Page 1')).toBeInTheDocument();
  });

  test('L-10: PF7 sends dir=B and the paging state of the page on show', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', [PAGE_1, PAGE_2, PAGE_1]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });
    await screen.findByText('Page 2');
    fireEvent.keyDown(window, { key: 'F7' });

    await screen.findByText('Page 1');
    const requests = backend.callsTo('GET', '/api/cards');
    expect(requests).toHaveLength(3);
    expect(requests[2].url).toEqual(
      '/api/cards?dir=B&pageNumber=2&firstCardNum=4000000000000008'
      + '&lastCardNum=4000000000000014&nextPageExists=false&lastPageShown=true',
    );
  });

  test('L-11: PF7 on page 1 shows the no-previous literal', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', [PAGE_1, { ...PAGE_1, errorMessage: NO_PREVIOUS_PAGES }]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F7' });

    await waitFor(() => expect(message()).toEqual(NO_PREVIOUS_PAGES));
  });
});

describe('CCLI List Credit Cards — filters (L-03, L-04)', () => {
  test('L-03: a bad account filter shows the account-filter literal', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', [PAGE_1, { status: 400, body: { message: ACCOUNT_FILTER_MSG } }]);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText(/^Account Number/), { target: { value: '123' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER' }));

    await waitFor(() => expect(message()).toEqual(ACCOUNT_FILTER_MSG));
  });

  test('L-04: a bad card filter shows the card-filter literal', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', [PAGE_1, { status: 400, body: { message: CARD_FILTER_MSG } }]);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText(/^Credit Card Number/), { target: { value: '40001' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER' }));

    await waitFor(() => expect(message()).toEqual(CARD_FILTER_MSG));
  });
});

describe('CCLI List Credit Cards — selection flags (L-12…L-19)', () => {
  test('L-18: `S` transfers the row to COCRDSLC with the row keys', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', PAGE_1);
    backend.post('/api/cards/selection', {
      program: 'COCRDSLC',
      acctId: '00000000011',
      cardNum: '4000000000000003',
    });

    const view = await renderList(backend);
    fireEvent.change(screen.getByLabelText('select 4000000000000003'), { target: { value: 'S' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER' }));

    await waitFor(() => expect(currentUrl(view.location))
      .toEqual('/cards/view?acctId=00000000011&cardNum=4000000000000003'));
    // The map posts all seven CRDSELnn fields in row order (L-16).
    expect(backend.callsTo('POST', '/api/cards/selection')[0].body.flags)
      .toEqual(['', '', 'S', '', '', '', '']);
  });

  test('L-19: `U` transfers the row to COCRDUPC', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', PAGE_1);
    backend.post('/api/cards/selection', {
      program: 'COCRDUPC',
      acctId: '00000000011',
      cardNum: '4000000000000001',
    });

    const view = await renderList(backend);
    fireEvent.change(screen.getByLabelText('select 4000000000000001'), { target: { value: 'U' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER' }));

    await waitFor(() => expect(currentUrl(view.location))
      .toEqual('/cards/update?acctId=00000000011&cardNum=4000000000000001'));
  });

  test('L-16: two flagged rows are refused with the one-record literal', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', PAGE_1);
    backend.post('/api/cards/selection', { status: 400, body: { message: ONLY_ONE_RECORD } });

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('select 4000000000000001'), { target: { value: 'S' } });
    fireEvent.change(screen.getByLabelText('select 4000000000000004'), { target: { value: 'S' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER' }));

    await waitFor(() => expect(message()).toEqual(ONLY_ONE_RECORD));
    expect(backend.callsTo('POST', '/api/cards/selection')[0].body.flags)
      .toEqual(['S', '', '', 'S', '', '', '']);
  });

  test('L-17: a flag that is not S or U is refused with the invalid-action literal', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', PAGE_1);
    backend.post('/api/cards/selection', { status: 400, body: { message: INVALID_ACTION_CODE } });

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('select 4000000000000002'), { target: { value: 'X' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER' }));

    await waitFor(() => expect(message()).toEqual(INVALID_ACTION_CODE));
  });

  test('L-06: ENTER with every flag blank re-browses and posts no selection', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', PAGE_1);

    await renderList(backend);
    fireEvent.click(screen.getByRole('button', { name: 'ENTER' }));

    await waitFor(() => expect(backend.callsTo('GET', '/api/cards')).toHaveLength(2));
    expect(backend.callsTo('POST', '/api/cards/selection')).toHaveLength(0);
  });
});

describe('CCLI List Credit Cards — keys (L-20)', () => {
  test('L-20: PF3 leaves for the main menu', async () => {
    const backend = stubBackend();
    backend.get('/api/cards', PAGE_1);

    const view = await renderList(backend);
    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/menu'));
  });
});
