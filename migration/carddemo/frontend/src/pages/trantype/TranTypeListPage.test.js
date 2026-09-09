/**
 * CTLI — Maintain Transaction Type, list map (COTRTLIC / CTRTLIA).
 *
 * Oracle: `app/app-transaction-type-db2/cbl/COTRTLIC.cbl` and
 * `docs/migration/streams/TransactionTypeManagement/programs/COTRTLIC_functional_requirement.md`
 * (§3 validation, §4 business rules, §5 error paths, §6 control flow).
 *
 * CTLI is a pseudo conversation: every AID is one round trip that posts the
 * unprotected fields plus the COMMAREA (`state`) and paints the map that comes
 * back. The screen contract these tests hold is therefore (a) what each key
 * posts and (b) that the returned literals reach the INFOMSG/ERRMSG lines
 * untouched. Note the row action codes are `U` and `D` — CTLI has no `S`
 * (COTRTLIC.cbl:1034-1038).
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import TranTypeListPage from './TranTypeListPage';
import { renderScreen, stubBackend } from '../../testing/screenHarness';

const LIST_URL = '/api/admin/transaction-types/list';
const MAX_SCREEN_LINES = 7;

// COTRTLIC literals, verbatim (§5 of the functional requirement).
const INFO_DEFAULT = 'Type U to update, D to delete any record';
const INFO_UPDATE_ARMED = 'Update HIGHLIGHTED row. Press F10 to save';
const INFO_DELETE_ARMED = 'Delete HIGHLIGHTED row ? Press F10 to confirm';
const NO_MORE_PAGES = 'No more pages to display';
const NO_PREVIOUS_PAGES = 'No previous pages to display';
const INVALID_ACTION = 'Action code selected is invalid';
const ONLY_ONE_ACTION = 'Please select only 1 action';
const TYPE_FILTER_MSG = 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER';

function row(n) {
  return {
    typeCode: String(n).padStart(2, '0'),
    description: `TRANSACTION TYPE ${n}`,
    selection: '',
    highlighted: false,
    inError: false,
  };
}

function map(from, pageNumber, extra = {}) {
  return {
    typeFilter: '',
    descFilter: '',
    rows: Array.from({ length: MAX_SCREEN_LINES }, (unused, i) => row(from + i)),
    pageNumber,
    infoMessage: INFO_DEFAULT,
    errorMessage: '',
    protectSelectRows: false,
    state: { pageNumber },
    ...extra,
  };
}

const PAGE_1 = map(1, 1);
const PAGE_2 = map(8, 2);

function infoLine() {
  return screen.getByText(INFO_DEFAULT, { exact: true });
}

function errorLine() {
  return screen.getByRole('alert').textContent;
}

async function renderList(backend) {
  const view = renderScreen(<TranTypeListPage />);
  // Row descriptions are unprotected fields, so they are input values.
  await screen.findByDisplayValue('TRANSACTION TYPE 1');
  return view;
}

function bodies(backend) {
  return backend.callsTo('POST', LIST_URL).map((call) => call.body);
}

describe('CTLI Maintain Transaction Type — entry and paging (E1, B1, B2, §5)', () => {
  test('E1: first entry posts ENTER with no COMMAREA and seven blank rows', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, PAGE_1);

    await renderList(backend);

    const [first] = bodies(backend);
    expect(first.aid).toEqual('ENTER');
    expect(first.state).toBeNull();
    expect(first.typeFilter).toEqual('');
    expect(first.descFilter).toEqual('');
    expect(first.rows).toEqual(Array.from({ length: MAX_SCREEN_LINES }, () => ({
      selection: '',
      description: '',
    })));
  });

  test('B1: the map shows seven data rows', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, PAGE_1);

    await renderList(backend);

    expect(screen.getAllByRole('row')).toHaveLength(MAX_SCREEN_LINES + 1); // header + 7
    expect(screen.getByText('Page 1')).toBeInTheDocument();
  });

  test('B2: F8 posts PF8 with the COMMAREA it was handed and paints the next page', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [PAGE_1, PAGE_2]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await screen.findByDisplayValue('TRANSACTION TYPE 8');
    const [, second] = bodies(backend);
    expect(second.aid).toEqual('PF8');
    expect(second.state).toEqual({ pageNumber: 1 });
    expect(screen.getByText('Page 2')).toBeInTheDocument();
  });

  test('B2: F7 posts PF7 with the COMMAREA it was handed', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [PAGE_1, PAGE_2, PAGE_1]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });
    await screen.findByDisplayValue('TRANSACTION TYPE 8');
    fireEvent.keyDown(window, { key: 'F7' });

    await screen.findByDisplayValue('TRANSACTION TYPE 1');
    const [, , third] = bodies(backend);
    expect(third.aid).toEqual('PF7');
    expect(third.state).toEqual({ pageNumber: 2 });
  });

  test('§5: F8 past the last page shows the paging literal verbatim', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [PAGE_1, map(1, 1, { errorMessage: NO_MORE_PAGES })]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await waitFor(() => expect(errorLine()).toEqual(NO_MORE_PAGES));
  });

  test('§5: F7 on page 1 shows the paging literal verbatim', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [PAGE_1, map(1, 1, { errorMessage: NO_PREVIOUS_PAGES })]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F7' });

    await waitFor(() => expect(errorLine()).toEqual(NO_PREVIOUS_PAGES));
  });
});

describe('CTLI Maintain Transaction Type — row actions (V2, V6, V7, B5)', () => {
  test('the default info line is the action prompt, verbatim', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, PAGE_1);

    await renderList(backend);

    expect(infoLine()).toBeInTheDocument();
  });

  test('V6/B5: `U` posts the seven action codes in row order and arms the update', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [
      PAGE_1,
      map(1, 1, {
        infoMessage: INFO_UPDATE_ARMED,
        rows: PAGE_1.rows.map((r, i) => (i === 2 ? { ...r, highlighted: true } : r)),
      }),
    ]);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('select line 3'), { target: { value: 'U' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER=Process' }));

    await screen.findByText(INFO_UPDATE_ARMED, { exact: true });
    const [, second] = bodies(backend);
    expect(second.aid).toEqual('ENTER');
    expect(second.rows.map((r) => r.selection)).toEqual(['', '', 'U', '', '', '', '']);
  });

  test('B5: F10 commits the armed action as its own keystroke', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [
      PAGE_1,
      map(1, 1, { infoMessage: INFO_UPDATE_ARMED }),
      map(1, 1, { infoMessage: 'HIGHLIGHTED row was updated' }),
    ]);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('select line 1'), { target: { value: 'U' } });
    fireEvent.change(screen.getByLabelText('description line 1'), { target: { value: 'NEW DESC' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER=Process' }));
    await screen.findByText(INFO_UPDATE_ARMED, { exact: true });
    fireEvent.keyDown(window, { key: 'F10' });

    await screen.findByText('HIGHLIGHTED row was updated', { exact: true });
    expect(bodies(backend)[2].aid).toEqual('PF10');
  });

  test('V6: `D` arms the delete confirmation, verbatim', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [PAGE_1, map(1, 1, { infoMessage: INFO_DELETE_ARMED })]);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('select line 5'), { target: { value: 'D' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER=Process' }));

    await screen.findByText(INFO_DELETE_ARMED, { exact: true });
    expect(bodies(backend)[1].rows.map((r) => r.selection))
      .toEqual(['', '', '', '', 'D', '', '']);
  });

  test('V6: any other action code is refused verbatim', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [PAGE_1, map(1, 1, { errorMessage: INVALID_ACTION })]);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('select line 2'), { target: { value: 'S' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER=Process' }));

    await waitFor(() => expect(errorLine()).toEqual(INVALID_ACTION));
  });

  test('V7: two action codes are refused verbatim and both are posted', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [PAGE_1, map(1, 1, { errorMessage: ONLY_ONE_ACTION })]);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('select line 1'), { target: { value: 'U' } });
    fireEvent.change(screen.getByLabelText('select line 4'), { target: { value: 'D' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER=Process' }));

    await waitFor(() => expect(errorLine()).toEqual(ONLY_ONE_ACTION));
    expect(bodies(backend)[1].rows.map((r) => r.selection))
      .toEqual(['U', '', '', 'D', '', '', '']);
  });

  test('V2: a non-numeric Type Filter is refused verbatim', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [PAGE_1, map(1, 1, {
      typeFilter: 'AB',
      errorMessage: TYPE_FILTER_MSG,
      protectSelectRows: true,
    })]);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('Type Filter:'), { target: { value: 'AB' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER=Process' }));

    await waitFor(() => expect(errorLine()).toEqual(TYPE_FILTER_MSG));
    expect(bodies(backend)[1].typeFilter).toEqual('AB');
    // The select column is protected while a filter is in error (:1329-1373).
    expect(screen.getByLabelText('select line 1')).toBeDisabled();
  });
});

describe('CTLI Maintain Transaction Type — control flow (§6)', () => {
  test('F3 hands control to the admin menu (COADM01C)', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [PAGE_1, map(1, 1, { nextProgram: 'COADM01C' })]);

    const view = await renderList(backend);
    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/admin'));
    expect(bodies(backend)[1].aid).toEqual('PF3');
  });

  test('F2 hands control to CTTU (COTRTUPC) to add a type', async () => {
    const backend = stubBackend();
    backend.post(LIST_URL, [PAGE_1, map(1, 1, { nextProgram: 'COTRTUPC' })]);

    const view = await renderList(backend);
    fireEvent.keyDown(window, { key: 'F2' });

    await waitFor(() => expect(view.location.pathname)
      .toEqual('/admin/transaction-types/update'));
    expect(bodies(backend)[1].aid).toEqual('PF2');
  });
});
