/**
 * CT02 — Add Transaction (COTRN02C, map COTRN2A).
 *
 * Oracle: `app/cbl/COTRN02C.cbl` (the confirm gate at :176-190, the key edits at
 * :193-230, VALIDATE-INPUT-DATA-FIELDS at :251-436, the CARDXREF reads at
 * :593-626 and the success STRING at :727-733) and
 * `migration/transaction-management/docs/TransactionManagement_functional_requirement.md`.
 *
 * COTRN02C stops at the first failing edit, so each message is driven by one
 * keystroke and asserted literally.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import AddTransactionPage from './AddTransactionPage';
import { renderScreen, stubBackend } from '../../testing/screenHarness';

const TRANSACTIONS_URL = '/api/transactions';
const XREF_URL = '/api/cardxref/resolve';
const LATEST_URL = '/api/transactions/latest';

// COTRN02C literals, verbatim.
const CONFIRM_TO_ADD = 'Confirm to add this transaction...';
const INVALID_CONFIRM = 'Invalid value. Valid values are (Y/N)...';
const ACCT_NUMERIC = 'Account ID must be Numeric...';
const CARD_NUMERIC = 'Card Number must be Numeric...';
const KEY_REQUIRED = 'Account or Card Number must be entered...';
const TYPE_EMPTY = 'Type CD can NOT be empty...';
const AMOUNT_EMPTY = 'Amount can NOT be empty...';
const MERCHANT_ZIP_EMPTY = 'Merchant Zip can NOT be empty...';
const TYPE_NUMERIC = 'Type CD must be Numeric...';
const CATEGORY_NUMERIC = 'Category CD must be Numeric...';
const MERCHANT_ID_NUMERIC = 'Merchant ID must be Numeric...';
const AMOUNT_FORMAT = 'Amount should be in format -99999999.99';
const ORIG_DATE_FORMAT = 'Orig Date should be in format YYYY-MM-DD';
const PROC_DATE_FORMAT = 'Proc Date should be in format YYYY-MM-DD';
const ORIG_DATE_INVALID = 'Orig Date - Not a valid date...';
const PROC_DATE_INVALID = 'Proc Date - Not a valid date...';
const ACCT_NOT_FOUND = 'Account ID NOT found...';
const CARD_NOT_FOUND = 'Card Number NOT found...';
const DUPLICATE = 'Tran ID already exist...';
// STRING 'Transaction added successfully. ' + ' Your Tran ID is ' — two spaces
// between the sentences, as the two literals each carry one (cbl:727-733).
const ADDED = 'Transaction added successfully.  Your Tran ID is 0000000000000456.';

const VALID_FORM = {
  'Account ID': '00000000011',
  'Card Number': '4000000000000001',
  'Type CD': '01',
  'Category CD': '0001',
  Source: 'POS TERM',
  Description: 'PURCHASE',
  Amount: '-00000100.00',
  'Orig Date': '2022-06-01',
  'Proc Date': '2022-06-02',
  'Merchant ID': '000000001',
  'Merchant Name': 'ACME',
  'Merchant City': 'ANYTOWN',
  'Merchant Zip': '12345',
};

function messageLine() {
  return screen.getByRole('alert').textContent;
}

function fill(overrides = {}) {
  const values = { ...VALID_FORM, ...overrides };
  Object.entries(values).forEach(([label, value]) => {
    fireEvent.change(screen.getByLabelText(`${label}:`), { target: { value } });
  });
}

function enter() {
  fireEvent.click(screen.getByRole('button', { name: 'ENTER — Add' }));
}

describe('CT02 Add Transaction — key fields (FR-A1…FR-A3)', () => {
  test('FR-A3: neither key entered is refused verbatim', async () => {
    const backend = stubBackend();
    backend.post(TRANSACTIONS_URL, { status: 400, body: { message: KEY_REQUIRED } });

    renderScreen(<AddTransactionPage />);
    fill({ 'Account ID': '', 'Card Number': '' });
    enter();

    await waitFor(() => expect(messageLine()).toEqual(KEY_REQUIRED));
  });

  test('FR-A1: an Account ID resolves the Card Number through CARDXREF', async () => {
    const backend = stubBackend();
    backend.get(XREF_URL, { accountId: '00000000011', cardNumber: '4000000000000001' });

    renderScreen(<AddTransactionPage />);
    const acct = screen.getByLabelText('Account ID:');
    fireEvent.change(acct, { target: { value: '00000000011' } });
    fireEvent.blur(acct);

    await waitFor(() => expect(screen.getByLabelText('Card Number:'))
      .toHaveValue('4000000000000001'));
    expect(backend.callsTo('GET', XREF_URL)[0].url)
      .toEqual('/api/cardxref/resolve?accountId=00000000011');
  });

  test('FR-A1: an unknown account is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get(XREF_URL, { status: 404, body: { message: ACCT_NOT_FOUND } });

    renderScreen(<AddTransactionPage />);
    const acct = screen.getByLabelText('Account ID:');
    fireEvent.change(acct, { target: { value: '99999999999' } });
    fireEvent.blur(acct);

    await waitFor(() => expect(messageLine()).toEqual(ACCT_NOT_FOUND));
  });

  test('FR-A2: a Card Number alone resolves the Account ID', async () => {
    const backend = stubBackend();
    backend.get(XREF_URL, { accountId: '00000000011', cardNumber: '4000000000000001' });

    renderScreen(<AddTransactionPage />);
    const card = screen.getByLabelText('Card Number:');
    fireEvent.change(card, { target: { value: '4000000000000001' } });
    fireEvent.blur(card);

    await waitFor(() => expect(screen.getByLabelText('Account ID:')).toHaveValue('00000000011'));
    expect(backend.callsTo('GET', XREF_URL)[0].url)
      .toEqual('/api/cardxref/resolve?cardNumber=4000000000000001');
  });

  test('FR-A2: an unknown card is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get(XREF_URL, { status: 404, body: { message: CARD_NOT_FOUND } });

    renderScreen(<AddTransactionPage />);
    const card = screen.getByLabelText('Card Number:');
    fireEvent.change(card, { target: { value: '4000000000000009' } });
    fireEvent.blur(card);

    await waitFor(() => expect(messageLine()).toEqual(CARD_NOT_FOUND));
  });
});

describe('CT02 Add Transaction — field edits (FR-A4…FR-A10)', () => {
  // One case per literal in VALIDATE-INPUT-DATA-FIELDS; the program stops at the
  // first failure, so the offending field is the only one changed.
  const cases = [
    ['a non-numeric Account ID', { 'Account ID': 'ABCDEFGHIJK' }, ACCT_NUMERIC],
    ['a non-numeric Card Number', { 'Card Number': 'ABCD000000000001' }, CARD_NUMERIC],
    ['an empty Type CD', { 'Type CD': '' }, TYPE_EMPTY],
    ['an empty Amount', { Amount: '' }, AMOUNT_EMPTY],
    ['an empty Merchant Zip', { 'Merchant Zip': '' }, MERCHANT_ZIP_EMPTY],
    ['a non-numeric Type CD', { 'Type CD': 'AB' }, TYPE_NUMERIC],
    ['a non-numeric Category CD', { 'Category CD': 'ABCD' }, CATEGORY_NUMERIC],
    ['a non-numeric Merchant ID', { 'Merchant ID': 'ABCDEFGHI' }, MERCHANT_ID_NUMERIC],
    ['a badly formatted Amount', { Amount: '100' }, AMOUNT_FORMAT],
    ['a badly formatted Orig Date', { 'Orig Date': '06/01/2022' }, ORIG_DATE_FORMAT],
    ['a badly formatted Proc Date', { 'Proc Date': '06/02/2022' }, PROC_DATE_FORMAT],
    ['an impossible Orig Date', { 'Orig Date': '2022-02-30' }, ORIG_DATE_INVALID],
    ['an impossible Proc Date', { 'Proc Date': '2022-13-01' }, PROC_DATE_INVALID],
  ];

  test.each(cases)('FR-A4/FR-A10: %s is refused verbatim', async (name, overrides, expected) => {
    const backend = stubBackend();
    backend.post(TRANSACTIONS_URL, { status: 400, body: { message: expected } });

    renderScreen(<AddTransactionPage />);
    fill(overrides);
    enter();

    await waitFor(() => expect(messageLine()).toEqual(expected));
  });
});

describe('CT02 Add Transaction — confirm gate and write (FR-A6, FR-A11)', () => {
  test('FR-A11: a blank Confirm asks for confirmation, verbatim', async () => {
    const backend = stubBackend();
    backend.post(TRANSACTIONS_URL, { status: 400, body: { message: CONFIRM_TO_ADD } });

    renderScreen(<AddTransactionPage />);
    fill();
    enter();

    await waitFor(() => expect(messageLine()).toEqual(CONFIRM_TO_ADD));
    expect(backend.callsTo('POST', TRANSACTIONS_URL)[0].body.confirm).toEqual('');
  });

  test('FR-A11: a Confirm flag other than Y/N is refused verbatim', async () => {
    const backend = stubBackend();
    backend.post(TRANSACTIONS_URL, { status: 400, body: { message: INVALID_CONFIRM } });

    renderScreen(<AddTransactionPage />);
    fill();
    fireEvent.change(screen.getByLabelText('Confirm (Y/N):'), { target: { value: 'X' } });
    enter();

    await waitFor(() => expect(messageLine()).toEqual(INVALID_CONFIRM));
  });

  test('FR-A6: `Y` writes the record and reports the new Tran ID', async () => {
    const backend = stubBackend();
    backend.post(TRANSACTIONS_URL, { id: '0000000000000456', message: ADDED });

    renderScreen(<AddTransactionPage />);
    fill();
    fireEvent.change(screen.getByLabelText('Confirm (Y/N):'), { target: { value: 'Y' } });
    enter();

    await waitFor(() => expect(messageLine()).toEqual(ADDED));
    // INITIALIZE-ALL-FIELDS after the WRITE (cbl:724-726).
    expect(screen.getByLabelText('Account ID:')).toHaveValue('');
    expect(screen.getByLabelText('Merchant Zip:')).toHaveValue('');
    const posted = backend.callsTo('POST', TRANSACTIONS_URL)[0].body;
    expect(posted.accountId).toEqual('00000000011');
    expect(posted.amount).toEqual('-00000100.00');
    expect(posted.confirm).toEqual('Y');
  });

  test('FR-A6: a duplicate Tran ID is refused verbatim', async () => {
    const backend = stubBackend();
    backend.post(TRANSACTIONS_URL, { status: 409, body: { message: DUPLICATE } });

    renderScreen(<AddTransactionPage />);
    fill();
    fireEvent.change(screen.getByLabelText('Confirm (Y/N):'), { target: { value: 'Y' } });
    enter();

    await waitFor(() => expect(messageLine()).toEqual(DUPLICATE));
  });
});

describe('CT02 Add Transaction — keys (FR-A12, FR-A13, FR-A14)', () => {
  test('FR-A12: PF5 copies the data fields of the latest transaction', async () => {
    const backend = stubBackend();
    backend.get(LATEST_URL, {
      typeCd: '02',
      catCd: '0002',
      source: 'POS TERM',
      description: 'BILL PAYMENT - ONLINE',
      amountDisplay: '-00000250.00',
      origTs: '2022-06-03',
      procTs: '2022-06-04',
      merchantId: '999999999',
      merchantName: 'BILL PAYMENT',
      merchantCity: 'N/A',
      merchantZip: 'N/A',
    });

    renderScreen(<AddTransactionPage />);
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(screen.getByLabelText('Description:'))
      .toHaveValue('BILL PAYMENT - ONLINE'));
    expect(screen.getByLabelText('Amount:')).toHaveValue('-00000250.00');
    expect(screen.getByLabelText('Merchant Name:')).toHaveValue('BILL PAYMENT');
    // The keys are left as entered (COPY-LAST-TRAN-DATA, cbl:471-495).
    expect(screen.getByLabelText('Account ID:')).toHaveValue('');
  });

  test('FR-A12: PF5 with no transaction on file falls back to the lookup literal', async () => {
    const backend = stubBackend();
    backend.get(LATEST_URL, { status: 204, body: null });

    renderScreen(<AddTransactionPage />);
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(messageLine()).toEqual('Unable to lookup Transaction...'));
  });

  test('FR-A13: PF4 clears every field and the message line', async () => {
    const backend = stubBackend();
    backend.post(TRANSACTIONS_URL, { status: 400, body: { message: CONFIRM_TO_ADD } });

    renderScreen(<AddTransactionPage />);
    fill();
    enter();
    await waitFor(() => expect(messageLine()).toEqual(CONFIRM_TO_ADD));

    fireEvent.keyDown(window, { key: 'F4' });

    expect(screen.getByLabelText('Account ID:')).toHaveValue('');
    expect(screen.getByLabelText('Merchant Zip:')).toHaveValue('');
    expect(messageLine()).toEqual('');
  });

  test('FR-A14: PF3 returns to the main menu', async () => {
    stubBackend();

    const view = renderScreen(<AddTransactionPage />);
    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/menu'));
  });
});
