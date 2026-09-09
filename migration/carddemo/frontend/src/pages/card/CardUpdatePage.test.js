/**
 * CCUP — Update Credit Card Details (COCRDUPC, map CCRDUPA).
 *
 * Oracle: `app/cbl/COCRDUPC.cbl` (1000-SEND-MAP / EDIT-MAP-INPUTS at :1100-1420
 * and the CCUP-CHANGE-ACTION state machine at :948-1031), `app/bms/COCRDUP.bms`
 * for the labels, and
 * `docs/migration/streams/CardManagement/programs/COCRDUPC_functional_requirement.md`.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import CardUpdatePage from './CardUpdatePage';
import { renderScreen, stubBackend } from '../../testing/screenHarness';

const UPDATE_URL = '/api/cards/update';

// COCRDUPC literals, verbatim.
const PROMPT_KEYS = 'Please enter Account and Card Number';
const DETAILS_SHOWN = 'Details of selected card shown above';
const UPDATE_PRESENTED = 'Update card details presented above.';
const CHANGES_VALIDATED = 'Changes validated.Press F5 to save';
const CHANGES_COMMITTED = 'Changes committed to database';
const NO_CHANGE = 'No change detected with respect to values fetched.';
const NAME_MISSING = 'Card name not provided';
const NAME_ALPHA = 'Card name can only contain alphabets and spaces';
const STATUS_YN = 'Card Active Status must be Y or N';
const MONTH_RANGE = 'Card expiry month must be between 1 and 12';
const YEAR_INVALID = 'Invalid card expiry year';
const ACCT_MISSING = 'Account number not provided';
const CARD_MISSING = 'Card number not provided';
const NOT_FOUND = 'Did not find cards for this search condition';
const CHANGED_BY_OTHER = 'Record changed by some one else. Please review';
const COULD_NOT_LOCK = 'Could not lock record for update';
const UPDATE_FAILED = 'Update of record failed';
const CHANGES_UNSUCCESSFUL = 'Changes unsuccessful. Please try again';

const CARD = {
  acctId: '00000000011',
  cardNum: '4000000000000001',
  embossedName: 'JOHN DOE',
  activeStatus: 'Y',
  expiryMonth: '06',
  expiryYear: '2027',
  expiryDay: '30',
  cvvCd: '123',
  message: DETAILS_SHOWN,
  state: 'SHOW_DETAILS',
};

function errorLine() {
  return screen.getByRole('alert').textContent;
}

function infoLine() {
  // The info line is the sibling of the (empty) error line.
  return screen.getByRole('alert').nextSibling.textContent;
}

function renderSelected(backend) {
  backend.get(UPDATE_URL, CARD);
  return renderScreen(<CardUpdatePage />, {
    route: '/cards/update?acctId=00000000011&cardNum=4000000000000001',
  });
}

function enter() {
  fireEvent.click(screen.getByRole('button', { name: 'ENTER=Process' }));
}

describe('CCUP Card Update — entry (FR-U1…FR-U3)', () => {
  test('FR-U1: with no keys the screen asks for them, verbatim', async () => {
    stubBackend();

    renderScreen(<CardUpdatePage />, { route: '/cards/update' });

    expect(infoLine()).toEqual(PROMPT_KEYS);
  });

  test('FR-U3: a selected card is fetched and confirmed, verbatim', async () => {
    const backend = stubBackend();
    renderSelected(backend);

    await waitFor(() => expect(infoLine()).toEqual(DETAILS_SHOWN));
    expect(backend.callsTo('GET', UPDATE_URL)[0].url)
      .toEqual('/api/cards/update?acctId=00000000011&cardNum=4000000000000001');
    expect(screen.getByLabelText('Name on card :')).toHaveValue('JOHN DOE');
    expect(screen.getByLabelText('Card Active Y/N :')).toHaveValue('Y');
    expect(screen.getByLabelText('Expiry Date :')).toHaveValue('06');
    expect(screen.getByLabelText('Expiry year')).toHaveValue('2027');
  });

  test('FR-U2: an unknown key pair is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get(UPDATE_URL, { status: 404, body: { message: NOT_FOUND } });

    renderScreen(<CardUpdatePage />, {
      route: '/cards/update?acctId=00000000011&cardNum=4000000000009999',
    });

    await waitFor(() => expect(errorLine()).toEqual(NOT_FOUND));
  });

  test.each([
    ['a missing account number', ACCT_MISSING],
    ['a missing card number', CARD_MISSING],
  ])('FR-U2: %s is refused verbatim', async (name, expected) => {
    const backend = stubBackend();
    backend.get(UPDATE_URL, { status: 400, body: { message: expected } });

    renderScreen(<CardUpdatePage />, { route: '/cards/update?acctId=1&cardNum=2' });

    await waitFor(() => expect(errorLine()).toEqual(expected));
  });
});

describe('CCUP Card Update — field edits (FR-U10…FR-U14)', () => {
  test.each([
    ['a blank name', { label: 'Name on card :', value: '' }, NAME_MISSING],
    ['a name with digits', { label: 'Name on card :', value: 'JOHN 3RD' }, NAME_ALPHA],
    ['a status other than Y/N', { label: 'Card Active Y/N :', value: 'X' }, STATUS_YN],
    ['a month outside 1-12', { label: 'Expiry Date :', value: '13' }, MONTH_RANGE],
    ['a non-numeric year', { label: 'Expiry year', value: 'ABCD' }, YEAR_INVALID],
  ])('FR-U10/FR-U14: %s is refused verbatim', async (name, edit, expected) => {
    const backend = stubBackend();
    renderSelected(backend);
    await waitFor(() => expect(infoLine()).toEqual(DETAILS_SHOWN));

    backend.post(UPDATE_URL, { status: 400, body: { message: expected } });
    fireEvent.change(screen.getByLabelText(edit.label), { target: { value: edit.value } });
    enter();

    await waitFor(() => expect(errorLine()).toEqual(expected));
    // The rejected edit stays on the map for correction.
    expect(screen.getByLabelText(edit.label)).toHaveValue(edit.value);
  });

  test('FR-U9: re-sending the fetched values reports no change, verbatim', async () => {
    const backend = stubBackend();
    renderSelected(backend);
    await waitFor(() => expect(infoLine()).toEqual(DETAILS_SHOWN));

    backend.post(UPDATE_URL, { ...CARD, message: NO_CHANGE, state: 'NO_CHANGES' });
    enter();

    await waitFor(() => expect(infoLine()).toEqual(NO_CHANGE));
  });

  test('FR-U15: a valid edit is validated and asks for F5, verbatim', async () => {
    const backend = stubBackend();
    renderSelected(backend);
    await waitFor(() => expect(infoLine()).toEqual(DETAILS_SHOWN));

    backend.post(UPDATE_URL, { message: CHANGES_VALIDATED, state: 'CHANGES_OK_NOT_CONFIRMED' });
    fireEvent.change(screen.getByLabelText('Name on card :'), { target: { value: 'JANE DOE' } });
    enter();

    await waitFor(() => expect(infoLine()).toEqual(CHANGES_VALIDATED));
    // ENTER is not a commit: `confirmed` stays false until F5 (cbl:948-1031).
    const posted = backend.callsTo('POST', UPDATE_URL)[0].body;
    expect(posted.confirmed).toEqual(false);
    expect(posted.newName).toEqual('JANE DOE');
    expect(posted.oldName).toEqual('JOHN DOE');
  });
});

describe('CCUP Card Update — commit (FR-U16…FR-U21)', () => {
  async function editAndArm(backend) {
    renderSelected(backend);
    await waitFor(() => expect(infoLine()).toEqual(DETAILS_SHOWN));
    backend.post(UPDATE_URL, { message: CHANGES_VALIDATED, state: 'CHANGES_OK_NOT_CONFIRMED' });
    fireEvent.change(screen.getByLabelText('Name on card :'), { target: { value: 'JANE DOE' } });
    enter();
    await waitFor(() => expect(infoLine()).toEqual(CHANGES_VALIDATED));
  }

  test('FR-U16: F5 commits and confirms, verbatim', async () => {
    const backend = stubBackend();
    await editAndArm(backend);

    backend.post(UPDATE_URL, {
      ...CARD,
      embossedName: 'JANE DOE',
      message: CHANGES_COMMITTED,
      state: 'CHANGES_OKAYED_AND_DONE',
    });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(infoLine()).toEqual(CHANGES_COMMITTED));
    expect(backend.callsTo('POST', UPDATE_URL)[1].body.confirmed).toEqual(true);
    expect(screen.getByLabelText('Name on card :')).toHaveValue('JANE DOE');
  });

  test('FR-U18: a record changed by someone else is refused and re-displayed', async () => {
    const backend = stubBackend();
    await editAndArm(backend);

    backend.post(UPDATE_URL, {
      status: 409,
      body: {
        message: CHANGED_BY_OTHER,
        refreshed: { ...CARD, embossedName: 'SOMEONE ELSE' },
      },
    });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(errorLine()).toEqual(CHANGED_BY_OTHER));
    expect(screen.getByLabelText('Name on card :')).toHaveValue('SOMEONE ELSE');
  });

  test.each([
    ['the record cannot be locked', COULD_NOT_LOCK],
    ['the REWRITE fails', UPDATE_FAILED],
  ])('FR-U19: %s is reported verbatim', async (name, expected) => {
    const backend = stubBackend();
    await editAndArm(backend);

    backend.post(UPDATE_URL, { status: 500, body: { message: expected } });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(errorLine()).toEqual(expected));
  });

  test('FR-U19: an unexplained failure falls back to the generic literal', async () => {
    const backend = stubBackend();
    await editAndArm(backend);

    backend.post(UPDATE_URL, { status: 500, body: null });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(errorLine()).toEqual(CHANGES_UNSUCCESSFUL));
  });

  test('FR-U20: F12 discards the edits and re-reads the stored details', async () => {
    const backend = stubBackend();
    await editAndArm(backend);

    fireEvent.keyDown(window, { key: 'F12' });

    await waitFor(() => expect(screen.getByLabelText('Name on card :'))
      .toHaveValue('JOHN DOE'));
    expect(infoLine()).toEqual(DETAILS_SHOWN);
    expect(backend.callsTo('GET', UPDATE_URL)).toHaveLength(2);
  });

  test('FR-U21: F3 leaves the screen for the card list', async () => {
    const backend = stubBackend();
    const view = renderSelected(backend);
    await waitFor(() => expect(infoLine()).toEqual(DETAILS_SHOWN));

    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/cards'));
  });

  test('FR-U6: the update-in-progress banner is shown verbatim when the backend sends it', async () => {
    const backend = stubBackend();
    backend.get(UPDATE_URL, { ...CARD, message: UPDATE_PRESENTED });

    renderScreen(<CardUpdatePage />, {
      route: '/cards/update?acctId=00000000011&cardNum=4000000000000001',
    });

    await waitFor(() => expect(infoLine()).toEqual(UPDATE_PRESENTED));
  });
});
