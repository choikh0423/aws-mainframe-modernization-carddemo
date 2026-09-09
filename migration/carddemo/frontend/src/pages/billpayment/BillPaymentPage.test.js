/**
 * CB00 — Bill Payment (COBIL00C, map COBIL0A).
 *
 * Oracle: `app/cbl/COBIL00C.cbl` (PROCESS-ENTER-KEY at :154-243, the file
 * handlers at :355-543 and the success STRING at :527-531) and
 * `docs/migration/streams/BillPayment/programs/COBIL00C_functional_requirement.md`.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import BillPaymentPage from './BillPaymentPage';
import { renderScreen, stubBackend } from '../../testing/screenHarness';

const SCREEN_URL = '/api/billpay/screen';

// COBIL00C literals, verbatim.
const ACCT_EMPTY = 'Acct ID can NOT be empty...';
const INVALID_CONFIRM = 'Invalid value. Valid values are (Y/N)...';
const NOTHING_TO_PAY = 'You have nothing to pay...';
const CONFIRM_PAYMENT = 'Confirm to make a bill payment...';
const ACCT_NOT_FOUND = 'Account ID NOT found...';
const UNABLE_TO_LOOKUP = 'Unable to lookup Account...';
// STRING 'Payment successful. ' + ' Your Transaction ID is ' — the two literals
// each carry their own space, so the message has a double space (cbl:527-531).
const PAYMENT_SUCCESSFUL = 'Payment successful.  Your Transaction ID is 0000000000000123.';

function messageLine() {
  return screen.getByRole('alert').textContent;
}

function type({ accountId, confirm }) {
  if (accountId !== undefined) {
    fireEvent.change(screen.getByLabelText('Enter Acct ID:'), { target: { value: accountId } });
  }
  if (confirm !== undefined) {
    fireEvent.change(
      screen.getByLabelText('Do you want to pay your balance now. Please confirm:'),
      { target: { value: confirm } },
    );
  }
}

function enter() {
  fireEvent.click(screen.getByRole('button', { name: 'ENTER — Continue' }));
}

describe('CB00 Bill Payment — ENTER edits (FR-BP-10…FR-BP-16)', () => {
  test('FR-BP-10: a blank Acct ID is refused verbatim', async () => {
    const backend = stubBackend();
    backend.post(SCREEN_URL, { status: 400, body: { message: ACCT_EMPTY } });

    renderScreen(<BillPaymentPage />);
    enter();

    await waitFor(() => expect(messageLine()).toEqual(ACCT_EMPTY));
  });

  test('FR-BP-12: a confirm flag other than Y/N/blank is refused verbatim', async () => {
    const backend = stubBackend();
    backend.post(SCREEN_URL, { status: 400, body: { message: INVALID_CONFIRM } });

    renderScreen(<BillPaymentPage />);
    type({ accountId: '00000000011', confirm: 'X' });
    enter();

    await waitFor(() => expect(messageLine()).toEqual(INVALID_CONFIRM));
    // MOVE -1 TO CONFIRML: the cursor goes to the confirm field (cbl:185-190).
    expect(screen.getByLabelText('Do you want to pay your balance now. Please confirm:'))
      .toHaveFocus();
  });

  test('FR-BP-14: a zero or negative balance is refused verbatim', async () => {
    const backend = stubBackend();
    backend.post(SCREEN_URL, { status: 400, body: { message: NOTHING_TO_PAY } });

    renderScreen(<BillPaymentPage />);
    type({ accountId: '00000000011' });
    enter();

    await waitFor(() => expect(messageLine()).toEqual(NOTHING_TO_PAY));
  });

  test('FR-BP-15: a blank confirm shows the balance and asks for confirmation', async () => {
    const backend = stubBackend();
    backend.post(SCREEN_URL, {
      accountId: '00000000011',
      currentBalance: '000000250.00',
      confirm: '',
      message: CONFIRM_PAYMENT,
      messageColour: 'RED',
      cursor: 'CONFIRM',
    });

    renderScreen(<BillPaymentPage />);
    type({ accountId: '00000000011' });
    enter();

    await waitFor(() => expect(messageLine()).toEqual(CONFIRM_PAYMENT));
    expect(screen.getByText('000000250.00')).toBeInTheDocument();
    expect(backend.callsTo('POST', SCREEN_URL)[0].body)
      .toEqual({ accountId: '00000000011', confirm: '' });
  });

  test('FR-BP-11: an unknown account is refused verbatim', async () => {
    const backend = stubBackend();
    backend.post(SCREEN_URL, { status: 404, body: { message: ACCT_NOT_FOUND } });

    renderScreen(<BillPaymentPage />);
    type({ accountId: '99999999999' });
    enter();

    await waitFor(() => expect(messageLine()).toEqual(ACCT_NOT_FOUND));
    // The rejected turn leaves the typed account id on the map.
    expect(screen.getByLabelText('Enter Acct ID:')).toHaveValue('99999999999');
  });

  test('FR-BP-13: an ACCTDAT failure falls back to the WHEN OTHER literal', async () => {
    const backend = stubBackend();
    backend.post(SCREEN_URL, { status: 500, body: null });

    renderScreen(<BillPaymentPage />);
    type({ accountId: '00000000011' });
    enter();

    await waitFor(() => expect(messageLine()).toEqual(UNABLE_TO_LOOKUP));
  });
});

describe('CB00 Bill Payment — payment (FR-BP-17, FR-BP-39)', () => {
  test('FR-BP-17: `Y` pays and reports the transaction id, double space included', async () => {
    const backend = stubBackend();
    backend.post(SCREEN_URL, {
      accountId: '00000000011',
      currentBalance: '000000000.00',
      confirm: '',
      message: PAYMENT_SUCCESSFUL,
      messageColour: 'GREEN',
      cursor: 'ACTIDIN',
    });

    renderScreen(<BillPaymentPage />);
    type({ accountId: '00000000011', confirm: 'Y' });
    enter();

    await waitFor(() => expect(messageLine()).toEqual(PAYMENT_SUCCESSFUL));
    expect(backend.callsTo('POST', SCREEN_URL)[0].body)
      .toEqual({ accountId: '00000000011', confirm: 'Y' });
  });

  test('FR-BP-16: `N` blanks the map with no message (CLEAR-CURRENT-SCREEN)', async () => {
    const backend = stubBackend();
    backend.post(SCREEN_URL, {
      accountId: '',
      currentBalance: '',
      confirm: '',
      message: '',
      messageColour: 'RED',
      cursor: 'ACTIDIN',
    });

    renderScreen(<BillPaymentPage />);
    type({ accountId: '00000000011', confirm: 'N' });
    enter();

    await waitFor(() => expect(screen.getByLabelText('Enter Acct ID:')).toHaveValue(''));
    expect(messageLine()).toEqual('');
  });
});

describe('CB00 Bill Payment — keys (FR-BP-06, FR-BP-08)', () => {
  test('FR-BP-08: PF4 blanks every field and the message line', async () => {
    const backend = stubBackend();
    backend.post(SCREEN_URL, { status: 400, body: { message: ACCT_EMPTY } });

    renderScreen(<BillPaymentPage />);
    type({ accountId: '00000000011', confirm: 'X' });
    enter();
    await waitFor(() => expect(messageLine()).toEqual(ACCT_EMPTY));

    fireEvent.keyDown(window, { key: 'F4' });

    expect(screen.getByLabelText('Enter Acct ID:')).toHaveValue('');
    expect(screen.getByLabelText('Do you want to pay your balance now. Please confirm:'))
      .toHaveValue('');
    expect(messageLine()).toEqual('');
  });

  test('FR-BP-06: PF3 returns to the main menu', async () => {
    stubBackend();

    const view = renderScreen(<BillPaymentPage />);
    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/menu'));
  });
});
