/**
 * CTTU — Maintain Transaction Type (COTRTUPC, map COTRTUP).
 *
 * Oracle: `app/app-transaction-type-db2/cbl/COTRTUPC.cbl` (the info/error
 * literals declared at :150-200, 1210-EDIT-TRANTYPE at :820-843, the description
 * edit at :755-764 and the generic routines 1230-EDIT-ALPHANUM-REQD /
 * 1245-EDIT-NUM-REQD at :860-960) and
 * `docs/migration/streams/TransactionTypeManagement/programs/COTRTUPC_functional_requirement.md`.
 *
 * The edit messages are TRIM(WS-EDIT-VARIABLE-NAME) + a fixed suffix, so each
 * literal below is that composition character for character.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import TranTypeUpdatePage from './TranTypeUpdatePage';
import { renderScreen, stubBackend } from '../../testing/screenHarness';

const UPDATE_URL = '/api/admin/transaction-types/update';

// COTRTUPC literals, verbatim.
const ENTER_TYPE = 'Enter transaction type to be maintained';
const SELECTED_SHOWN = 'Selected transaction type shown above';
const UPDATE_SHOWN = 'Update transaction type details shown.';
const ENTER_NEW = 'Enter new transaction type details.';
const PRESS_F05_TO_ADD = 'Press F05 to add. F12 to cancel';
const CHANGES_VALIDATED = 'Changes validated.Press F5 to save';
const CHANGES_COMMITTED = 'Changes committed to database';
const CHANGES_UNSUCCESSFUL = 'Changes unsuccessful';
const DELETE_CONFIRM = 'Delete this record ? Press F4 to confirm';
const DELETE_SUCCESSFUL = 'Delete successful.';
const LOOKS_GOOD = 'Looks Good.... so far';
const NO_INPUT = 'No input received';
const NO_RECORD = 'No record found for this key in database';
const NO_CHANGE = 'No change detected with respect to values fetched.';
const INVALID_KEY = 'Invalid key pressed';
const COULD_NOT_LOCK = 'Could not lock record for update';
const CHANGED_BY_OTHER = 'Record changed by some one else. Please review';
const UPDATE_CANCELLED = 'Update was cancelled';
const UPDATE_FAILED = 'Update of record failed';
const DELETE_FAILED = 'Delete of record failed';
const DELETE_CANCELLED = 'Delete was cancelled';
const NAME_ALPHA = 'Name can only contain alphabets and spaces';
const TYPE_MANDATORY = 'Tran Type code must be supplied.';
const TYPE_NUMERIC = 'Tran Type code must be numeric.';
const DESC_MANDATORY = 'Transaction Desc must be supplied.';
const DESC_ALPHANUM = 'Transaction Desc can have numbers or alphabets only.';

const EMPTY_MAP = {
  typeCode: '',
  description: '',
  infoMessage: ENTER_TYPE,
  errorMessage: '',
  state: 'ENTER_KEY',
  typeCodeEditable: true,
  descriptionEditable: true,
};

function infoLine() {
  return screen.getByRole('alert').previousSibling.textContent;
}

function errorLine() {
  return screen.getByRole('alert').textContent;
}

function typeCodeField() {
  return screen.getByLabelText('Transaction Type :');
}

function descriptionField() {
  return screen.getByLabelText('Description :');
}

function enter() {
  fireEvent.click(screen.getByRole('button', { name: 'ENTER=Process' }));
}

/** Render the screen and let the EIBCALEN=0 first turn paint the empty map. */
async function renderEmpty(backend) {
  backend.post(UPDATE_URL, EMPTY_MAP);
  const view = renderScreen(<TranTypeUpdatePage />, { route: '/admin/transaction-types/update' });
  await waitFor(() => expect(infoLine()).toEqual(ENTER_TYPE));
  return view;
}

describe('CTTU Transaction Type Maintenance — entry', () => {
  test('FR-TU-01: the first turn paints the empty map with its prompt, verbatim', async () => {
    const backend = stubBackend();
    await renderEmpty(backend);

    expect(backend.callsTo('POST', UPDATE_URL)[0].body)
      .toEqual({ aid: 'ENTER', typeCode: '', description: '', state: null });
  });

  test('FR-TU-02: ENTER with an existing key shows the record, verbatim', async () => {
    const backend = stubBackend();
    await renderEmpty(backend);

    backend.post(UPDATE_URL, {
      typeCode: '01',
      description: 'PURCHASE',
      infoMessage: SELECTED_SHOWN,
      errorMessage: '',
      state: 'SHOW_DETAILS',
      typeCodeEditable: false,
      descriptionEditable: true,
      f4Enabled: true,
      f5Enabled: true,
      f12Enabled: true,
    });
    fireEvent.change(typeCodeField(), { target: { value: '01' } });
    enter();

    await waitFor(() => expect(infoLine()).toEqual(SELECTED_SHOWN));
    expect(descriptionField()).toHaveValue('PURCHASE');
    // The key is protected once the record is on the screen.
    expect(typeCodeField()).toHaveAttribute('readonly');
    expect(backend.callsTo('POST', UPDATE_URL)[1].body.state).toEqual('ENTER_KEY');
  });

  test('FR-TU-03: an unknown key offers the add path, verbatim', async () => {
    const backend = stubBackend();
    await renderEmpty(backend);

    backend.post(UPDATE_URL, {
      typeCode: '99',
      description: '',
      infoMessage: ENTER_NEW,
      errorMessage: NO_RECORD,
      state: 'NOT_FOUND',
      typeCodeEditable: false,
      descriptionEditable: true,
      f5Enabled: true,
      f12Enabled: true,
    });
    fireEvent.change(typeCodeField(), { target: { value: '99' } });
    enter();

    await waitFor(() => expect(errorLine()).toEqual(NO_RECORD));
    expect(infoLine()).toEqual(ENTER_NEW);
  });

  test.each([
    ['an empty map', NO_INPUT],
    ['a blank type code', TYPE_MANDATORY],
    ['a non-numeric type code', TYPE_NUMERIC],
  ])('FR-TU-04: %s is refused verbatim', async (name, expected) => {
    const backend = stubBackend();
    await renderEmpty(backend);

    backend.post(UPDATE_URL, { ...EMPTY_MAP, infoMessage: '', errorMessage: expected });
    enter();

    await waitFor(() => expect(errorLine()).toEqual(expected));
  });
});

describe('CTTU Transaction Type Maintenance — description edits', () => {
  async function shown(backend) {
    await renderEmpty(backend);
    backend.post(UPDATE_URL, {
      typeCode: '01',
      description: 'PURCHASE',
      infoMessage: SELECTED_SHOWN,
      errorMessage: '',
      state: 'SHOW_DETAILS',
      typeCodeEditable: false,
      descriptionEditable: true,
      f4Enabled: true,
      f5Enabled: true,
      f12Enabled: true,
    });
    fireEvent.change(typeCodeField(), { target: { value: '01' } });
    enter();
    await waitFor(() => expect(infoLine()).toEqual(SELECTED_SHOWN));
  }

  test.each([
    ['a blank description', '', DESC_MANDATORY],
    ['a description with punctuation', 'PURCHASE!', DESC_ALPHANUM],
    ['a name with digits where only letters are allowed', 'PURCHASE 1', NAME_ALPHA],
  ])('FR-TU-08: %s is refused verbatim', async (name, value, expected) => {
    const backend = stubBackend();
    await shown(backend);

    backend.post(UPDATE_URL, {
      typeCode: '01',
      description: value,
      infoMessage: '',
      errorMessage: expected,
      state: 'SHOW_DETAILS',
      typeCodeEditable: false,
      descriptionEditable: true,
    });
    fireEvent.change(descriptionField(), { target: { value } });
    enter();

    await waitFor(() => expect(errorLine()).toEqual(expected));
  });

  test('FR-TU-09: an untouched record reports no change, verbatim', async () => {
    const backend = stubBackend();
    await shown(backend);

    backend.post(UPDATE_URL, {
      typeCode: '01',
      description: 'PURCHASE',
      infoMessage: '',
      errorMessage: NO_CHANGE,
      state: 'SHOW_DETAILS',
      typeCodeEditable: false,
      descriptionEditable: true,
    });
    enter();

    await waitFor(() => expect(errorLine()).toEqual(NO_CHANGE));
  });

  test('FR-TU-10: a clean change is validated and asks for F5, verbatim', async () => {
    const backend = stubBackend();
    await shown(backend);

    backend.post(UPDATE_URL, {
      typeCode: '01',
      description: 'PURCHASES',
      infoMessage: CHANGES_VALIDATED,
      errorMessage: '',
      state: 'CHANGES_OK_NOT_CONFIRMED',
      typeCodeEditable: false,
      descriptionEditable: true,
      f5Enabled: true,
      f12Enabled: true,
    });
    fireEvent.change(descriptionField(), { target: { value: 'PURCHASES' } });
    enter();

    await waitFor(() => expect(infoLine()).toEqual(CHANGES_VALIDATED));
    expect(backend.callsTo('POST', UPDATE_URL)[2].body)
      .toEqual({
        aid: 'ENTER', typeCode: '01', description: 'PURCHASES', state: 'SHOW_DETAILS',
      });
  });

  test('FR-TU-11: F5 commits and confirms, verbatim', async () => {
    const backend = stubBackend();
    await shown(backend);

    backend.post(UPDATE_URL, {
      typeCode: '01',
      description: 'PURCHASES',
      infoMessage: CHANGES_COMMITTED,
      errorMessage: '',
      state: 'CHANGES_OKAYED_AND_DONE',
      typeCodeEditable: true,
      descriptionEditable: true,
    });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(infoLine()).toEqual(CHANGES_COMMITTED));
    expect(backend.callsTo('POST', UPDATE_URL)[2].body.aid).toEqual('PF5');
  });

  test.each([
    ['the record cannot be locked', COULD_NOT_LOCK],
    ['the record moved underneath', CHANGED_BY_OTHER],
    ['the update fails', UPDATE_FAILED],
    ['the update is refused outright', CHANGES_UNSUCCESSFUL],
    ['the update was cancelled', UPDATE_CANCELLED],
  ])('FR-TU-12: %s is reported verbatim', async (name, expected) => {
    const backend = stubBackend();
    await shown(backend);

    backend.post(UPDATE_URL, {
      typeCode: '01',
      description: 'PURCHASES',
      infoMessage: '',
      errorMessage: expected,
      state: 'SHOW_DETAILS',
      typeCodeEditable: false,
      descriptionEditable: true,
    });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(errorLine()).toEqual(expected));
  });
});

describe('CTTU Transaction Type Maintenance — add and delete', () => {
  test('FR-TU-06: the add path asks for F05, verbatim', async () => {
    const backend = stubBackend();
    await renderEmpty(backend);

    backend.post(UPDATE_URL, {
      typeCode: '99',
      description: 'NEW TYPE',
      infoMessage: PRESS_F05_TO_ADD,
      errorMessage: LOOKS_GOOD,
      state: 'ADD_OK_NOT_CONFIRMED',
      typeCodeEditable: false,
      descriptionEditable: true,
      f5Enabled: true,
      f12Enabled: true,
    });
    fireEvent.change(typeCodeField(), { target: { value: '99' } });
    fireEvent.change(descriptionField(), { target: { value: 'NEW TYPE' } });
    enter();

    await waitFor(() => expect(infoLine()).toEqual(PRESS_F05_TO_ADD));
    expect(errorLine()).toEqual(LOOKS_GOOD);
  });

  test('FR-TU-14: F4 arms the delete and asks for confirmation, verbatim', async () => {
    const backend = stubBackend();
    await renderEmpty(backend);

    backend.post(UPDATE_URL, {
      typeCode: '01',
      description: 'PURCHASE',
      infoMessage: DELETE_CONFIRM,
      errorMessage: '',
      state: 'DELETE_NOT_CONFIRMED',
      typeCodeEditable: false,
      descriptionEditable: false,
      f4Enabled: true,
      f12Enabled: true,
    });
    fireEvent.keyDown(window, { key: 'F4' });

    await waitFor(() => expect(infoLine()).toEqual(DELETE_CONFIRM));
    expect(backend.callsTo('POST', UPDATE_URL)[1].body.aid).toEqual('PF4');
    expect(descriptionField()).toHaveAttribute('readonly');
  });

  test('FR-TU-15: the confirmed delete reports success, verbatim', async () => {
    const backend = stubBackend();
    await renderEmpty(backend);

    backend.post(UPDATE_URL, {
      ...EMPTY_MAP,
      infoMessage: DELETE_SUCCESSFUL,
      state: 'ENTER_KEY',
    });
    fireEvent.keyDown(window, { key: 'F4' });

    await waitFor(() => expect(infoLine()).toEqual(DELETE_SUCCESSFUL));
  });

  test.each([
    ['a failed delete', DELETE_FAILED],
    ['a cancelled delete', DELETE_CANCELLED],
  ])('FR-TU-16: %s is reported verbatim', async (name, expected) => {
    const backend = stubBackend();
    await renderEmpty(backend);

    backend.post(UPDATE_URL, { ...EMPTY_MAP, infoMessage: '', errorMessage: expected });
    fireEvent.keyDown(window, { key: 'F4' });

    await waitFor(() => expect(errorLine()).toEqual(expected));
  });
});

describe('CTTU Transaction Type Maintenance — keys', () => {
  test('FR-TU-17: an unsupported key is refused verbatim', async () => {
    const backend = stubBackend();
    await renderEmpty(backend);

    backend.post(UPDATE_URL, { ...EMPTY_MAP, infoMessage: '', errorMessage: INVALID_KEY });
    fireEvent.keyDown(window, { key: 'F12' });

    await waitFor(() => expect(errorLine()).toEqual(INVALID_KEY));
  });

  test('FR-TU-18: F3 returns to the admin menu through CCARD-NEXT-PROG', async () => {
    const backend = stubBackend();
    const view = await renderEmpty(backend);

    backend.post(UPDATE_URL, { ...EMPTY_MAP, nextProgram: 'COADM01C' });
    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/admin'));
    expect(backend.callsTo('POST', UPDATE_URL)[1].body.aid).toEqual('PF3');
  });

  test('FR-TU-19: the F6=Add caption is never lit (COTRTUPC quirk)', async () => {
    const backend = stubBackend();
    await renderEmpty(backend);

    // No paragraph moves DFHBMASB into FKEY06A, so the map's F6 stays dark.
    expect(screen.queryByRole('button', { name: 'F6=Add' })).toBeNull();
  });

  test('FR-TU-20: the update-in-progress banner is shown verbatim', async () => {
    const backend = stubBackend();
    await renderEmpty(backend);

    backend.post(UPDATE_URL, {
      typeCode: '01',
      description: 'PURCHASE',
      infoMessage: UPDATE_SHOWN,
      errorMessage: '',
      state: 'SHOW_DETAILS',
      typeCodeEditable: false,
      descriptionEditable: true,
    });
    fireEvent.change(typeCodeField(), { target: { value: '01' } });
    enter();

    await waitFor(() => expect(infoLine()).toEqual(UPDATE_SHOWN));
  });
});
