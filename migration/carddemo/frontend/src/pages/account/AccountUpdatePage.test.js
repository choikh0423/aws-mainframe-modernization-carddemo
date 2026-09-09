/**
 * CAUP — Update Account (COACTUPC, map CACTUPA).
 *
 * Oracle: `app/cbl/COACTUPC.cbl` (the field edits driven from :1472-1675 through
 * the generic routines 1215/1220/1245/1250/1265/1270/1275/1280 at :1824-2560,
 * 2000-DECIDE-ACTION at :2562+ and 9600-WRITE-PROCESSING at :3892-3953) and
 * `docs/migration/streams/AccountManagement/programs/COACTUPC_functional_requirement.md`.
 *
 * The edit messages are composed as TRIM(WS-EDIT-VARIABLE-NAME) + a fixed
 * suffix, so each literal below is that composition, character for character.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import AccountUpdatePage from './AccountUpdatePage';
import { renderScreen, stubBackend } from '../../testing/screenHarness';

const FETCH_URL = '/api/accounts/00000000011/update';
const VALIDATE_URL = '/api/accounts/00000000011/update/validate';
const SAVE_URL = '/api/accounts/00000000011/update/save';

// COACTUPC literals, verbatim.
const PROMPT = 'Enter or update id of account to update';
const DETAILS_PRESENTED = 'Update account details presented above.';
const NO_INPUT = 'No input received';
const ACCT_FORMAT = 'Account Number if supplied must be a 11 digit Non-Zero Number';
const STATUS_YESNO = 'Account Status must be Y or N.';
const CREDIT_LIMIT_INVALID = 'Credit Limit is not valid';
const FIRST_NAME_MANDATORY = 'First Name must be supplied.';
const FICO_RANGE = 'FICO Score: should be between 300 and 850';
const SSN_FIRST3 = 'SSN: First 3 chars: should not be 000, 666, or between 900 and 999';
const STATE_INVALID = 'State: is not a valid state code';
const ZIP_FOR_STATE = 'Invalid zip code for state';
const PHONE_AREA = 'Phone Number 1: Area code must be A 3 digit number.';
const NO_CHANGE = 'No change detected with respect to values fetched.';
const CHANGES_VALIDATED = 'Changes validated.Press F5 to save';
const CHANGES_COMMITTED = 'Changes committed to database';
const LOCK_ACCOUNT = 'Could not lock account record for update';
const LOCK_CUSTOMER = 'Could not lock customer record for update';
const CHANGED_BY_OTHER = 'Record changed by some one else. Please review';
const UPDATE_FAILED = 'Update of record failed';
const CHANGES_UNSUCCESSFUL = 'Changes unsuccessful. Please try again';

const DETAILS = {
  activeStatus: 'Y',
  openYear: '2020', openMonth: '01', openDay: '01',
  creditLimit: '000000010000.00',
  expiryYear: '2027', expiryMonth: '12', expiryDay: '31',
  cashCreditLimit: '000000005000.00',
  reissueYear: '2024', reissueMonth: '05', reissueDay: '01',
  currBal: '000000000250.00',
  currCycCredit: '000000000000.00',
  groupId: 'DEFAULT',
  currCycDebit: '000000000000.00',
  custId: '000000011',
  ssnPart1: '123', ssnPart2: '45', ssnPart3: '6789',
  dobYear: '1980', dobMonth: '03', dobDay: '15',
  ficoScore: '700',
  firstName: 'JOHN', middleName: 'Q', lastName: 'DOE',
  addrLine1: '1 MAIN ST', addrLine2: '',
  state: 'NY', zip: '10001', city: 'NEW YORK', country: 'USA',
  phone1Area: '212', phone1Prefix: '555', phone1Line: '0100',
  govtIssuedId: 'ID-1',
  phone2Area: '212', phone2Prefix: '555', phone2Line: '0101',
  eftAccountId: 'EFT0000001',
  priCardHolderInd: 'Y',
};

const FETCHED = {
  state: 'DETAILS_FETCHED',
  infoMessage: DETAILS_PRESENTED,
  errorMessage: '',
  details: DETAILS,
};

function infoLine() {
  return screen.getByRole('alert').previousSibling.textContent;
}

function errorLine() {
  return screen.getByRole('alert').textContent;
}

function enter() {
  fireEvent.click(screen.getByRole('button', { name: 'ENTER — Process' }));
}

async function renderFetched(backend) {
  backend.get(FETCH_URL, FETCHED);
  const view = renderScreen(<AccountUpdatePage />, {
    route: '/accounts/update?accountId=00000000011',
  });
  await waitFor(() => expect(infoLine()).toEqual(DETAILS_PRESENTED));
  return view;
}

describe('CAUP Account Update — entry (U-1…U-8)', () => {
  test('U-1: the screen opens with the prompt, verbatim', () => {
    stubBackend();

    renderScreen(<AccountUpdatePage />, { route: '/accounts/update' });

    expect(infoLine()).toEqual(PROMPT);
  });

  test('U-8: a selected account is fetched and announced, verbatim', async () => {
    const backend = stubBackend();
    await renderFetched(backend);

    expect(backend.callsTo('GET', FETCH_URL)).toHaveLength(1);
    expect(screen.getByLabelText('First Name firstName')).toHaveValue('JOHN');
  });

  test.each([
    ['an empty account id', NO_INPUT],
    ['a non 11-digit account id', ACCT_FORMAT],
  ])('U-5/U-6: %s is refused verbatim', async (name, expected) => {
    const backend = stubBackend();
    backend.get(FETCH_URL, { status: 400, body: { message: expected } });

    renderScreen(<AccountUpdatePage />, { route: '/accounts/update?accountId=00000000011' });

    await waitFor(() => expect(errorLine()).toEqual(expected));
  });
});

describe('CAUP Account Update — field edits (U-15…U-19)', () => {
  // First error wins (U-15), so each case changes exactly one field.
  const cases = [
    ['a status other than Y/N', { label: 'Active Y/N: activeStatus', value: 'X' }, STATUS_YESNO],
    ['a non-numeric credit limit', { label: 'Credit Limit : creditLimit', value: 'ABC' }, CREDIT_LIMIT_INVALID],
    ['a blank first name', { label: 'First Name firstName', value: '' }, FIRST_NAME_MANDATORY],
    ['a FICO score out of range', { label: 'FICO Score: ficoScore', value: '900' }, FICO_RANGE],
    ['a reserved SSN prefix', { label: 'SSN: ssnPart1', value: '666' }, SSN_FIRST3],
    ['an unknown state code', { label: 'State state', value: 'XX' }, STATE_INVALID],
    ['a zip that does not belong to the state', { label: 'Zip zip', value: '99999' }, ZIP_FOR_STATE],
    ['a malformed phone area code', { label: 'Phone 1: phone1Area', value: 'AB' }, PHONE_AREA],
  ];

  test.each(cases)('U-15: %s is refused verbatim', async (name, edit, expected) => {
    const backend = stubBackend();
    await renderFetched(backend);

    backend.post(VALIDATE_URL, {
      state: 'DETAILS_FETCHED',
      infoMessage: '',
      errorMessage: expected,
      details: DETAILS,
    });
    fireEvent.change(screen.getByLabelText(edit.label), { target: { value: edit.value } });
    enter();

    await waitFor(() => expect(errorLine()).toEqual(expected));
    expect(infoLine()).toEqual('');
  });

  test('U-14: an untouched screen reports no change, verbatim', async () => {
    const backend = stubBackend();
    await renderFetched(backend);

    backend.post(VALIDATE_URL, {
      state: 'DETAILS_FETCHED',
      infoMessage: '',
      errorMessage: NO_CHANGE,
      details: DETAILS,
    });
    enter();

    await waitFor(() => expect(errorLine()).toEqual(NO_CHANGE));
    // The screen is posted as original + updated so the backend can compare.
    const posted = backend.callsTo('POST', VALIDATE_URL)[0].body;
    expect(posted.original).toEqual(DETAILS);
    expect(posted.updated).toEqual(DETAILS);
  });

  test('U-20: a clean change is validated and asks for F5, verbatim', async () => {
    const backend = stubBackend();
    await renderFetched(backend);

    backend.post(VALIDATE_URL, {
      state: 'CHANGES_OK_NOT_CONFIRMED',
      infoMessage: CHANGES_VALIDATED,
      errorMessage: '',
      details: { ...DETAILS, firstName: 'JANE' },
    });
    fireEvent.change(screen.getByLabelText('First Name firstName'), { target: { value: 'JANE' } });
    enter();

    await waitFor(() => expect(infoLine()).toEqual(CHANGES_VALIDATED));
    expect(backend.callsTo('POST', VALIDATE_URL)[0].body.updated.firstName).toEqual('JANE');
    // Once validated COACTUPC protects the fields until F5 or F12.
    expect(screen.getByLabelText('First Name firstName')).toHaveAttribute('readonly');
  });
});

describe('CAUP Account Update — save (U-21…U-25)', () => {
  async function validated(backend) {
    const view = await renderFetched(backend);
    backend.post(VALIDATE_URL, {
      state: 'CHANGES_OK_NOT_CONFIRMED',
      infoMessage: CHANGES_VALIDATED,
      errorMessage: '',
      details: { ...DETAILS, firstName: 'JANE' },
    });
    fireEvent.change(screen.getByLabelText('First Name firstName'), { target: { value: 'JANE' } });
    enter();
    await waitFor(() => expect(infoLine()).toEqual(CHANGES_VALIDATED));
    return view;
  }

  test('U-25: F5 commits and confirms, verbatim', async () => {
    const backend = stubBackend();
    await validated(backend);

    backend.post(SAVE_URL, {
      state: 'CHANGES_OKAYED_AND_DONE',
      infoMessage: CHANGES_COMMITTED,
      errorMessage: '',
      details: { ...DETAILS, firstName: 'JANE' },
    });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(infoLine()).toEqual(CHANGES_COMMITTED));
    expect(backend.callsTo('POST', SAVE_URL)).toHaveLength(1);
  });

  test.each([
    ['the account record cannot be locked', LOCK_ACCOUNT],
    ['the customer record cannot be locked', LOCK_CUSTOMER],
    ['the record moved underneath', CHANGED_BY_OTHER],
    ['the rewrite fails', UPDATE_FAILED],
  ])('U-21…U-24: %s is reported verbatim', async (name, expected) => {
    const backend = stubBackend();
    await validated(backend);

    backend.post(SAVE_URL, { status: 409, body: { message: expected } });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(errorLine()).toEqual(expected));
  });

  test('U-24: an unexplained save failure falls back to the generic literal', async () => {
    const backend = stubBackend();
    await validated(backend);

    backend.post(SAVE_URL, { status: 500, body: { message: CHANGES_UNSUCCESSFUL } });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(errorLine()).toEqual(CHANGES_UNSUCCESSFUL));
  });

  test('U-26: F5 before the changes are validated does nothing', async () => {
    const backend = stubBackend();
    await renderFetched(backend);

    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(infoLine()).toEqual(DETAILS_PRESENTED));
    expect(backend.callsTo('POST', SAVE_URL)).toHaveLength(0);
  });

  test('U-27: F12 abandons the edits and re-presents the stored record', async () => {
    const backend = stubBackend();
    await validated(backend);

    fireEvent.keyDown(window, { key: 'F12' });

    await waitFor(() => expect(screen.getByLabelText('First Name firstName'))
      .toHaveValue('JOHN'));
    expect(infoLine()).toEqual(DETAILS_PRESENTED);
    expect(backend.callsTo('GET', FETCH_URL)).toHaveLength(2);
  });

  test('U-28: F3 leaves the screen for the main menu', async () => {
    const backend = stubBackend();
    const view = await renderFetched(backend);

    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/menu'));
  });
});
