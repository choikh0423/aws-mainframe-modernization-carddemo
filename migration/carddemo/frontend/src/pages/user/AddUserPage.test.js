/**
 * CU01 — Add User (COUSR01C, map COUSR1A).
 *
 * Oracle: `app/cbl/COUSR01C.cbl` (the five empty-field edits at :115-160, the
 * DUPKEY/DUPREC arm at :260-266, the WHEN OTHER arm at :267-271 and the success
 * STRING at :250-259) and
 * `docs/migration/streams/UserManagement/programs/COUSR01C_functional_requirement.md`.
 *
 * COUSR01C edits the five fields in map order and stops at the first failure,
 * so the messages are asserted one keystroke at a time, verbatim — the three
 * trailing dots and the single space before them are part of the literal.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import AddUserPage from './AddUserPage';
import { renderScreen, stubBackend } from '../../testing/screenHarness';

const USERS_URL = '/api/admin/users';

// COUSR01C literals, verbatim.
const FIRST_NAME_EMPTY = 'First Name can NOT be empty...';
const LAST_NAME_EMPTY = 'Last Name can NOT be empty...';
const USER_ID_EMPTY = 'User ID can NOT be empty...';
const PASSWORD_EMPTY = 'Password can NOT be empty...';
const USER_TYPE_EMPTY = 'User Type can NOT be empty...';
const ALREADY_EXIST = 'User ID already exist...';
const UNABLE_TO_ADD = 'Unable to Add User...';
const INVALID_KEY = 'Invalid key pressed. Please see below...';
// STRING 'User ' / SEC-USR-ID DELIMITED BY SPACE / ' has been added ...'.
const ADDED = 'User USER0001 has been added ...';

function errorLine() {
  return screen.getByRole('alert').textContent;
}

function statusLine() {
  return screen.getByRole('status').textContent;
}

function fill({
  firstName = 'JOHN',
  lastName = 'DOE',
  userId = 'USER0001',
  password = 'PASS0001',
  userType = 'U',
} = {}) {
  fireEvent.change(screen.getByLabelText('First Name:'), { target: { value: firstName } });
  fireEvent.change(screen.getByLabelText('Last Name:'), { target: { value: lastName } });
  fireEvent.change(screen.getByLabelText('User ID:'), { target: { value: userId } });
  fireEvent.change(screen.getByLabelText('Password:'), { target: { value: password } });
  fireEvent.change(screen.getByLabelText('User Type:'), { target: { value: userType } });
}

function enter() {
  fireEvent.click(screen.getByRole('button', { name: 'ENTER — Add User' }));
}

describe('CU01 Add User — field edits (FR-UA-3)', () => {
  // Each edit is reached by leaving exactly that field blank; the program stops
  // at the first failure, so one keystroke yields exactly one message.
  const cases = [
    ['First Name', { firstName: '' }, FIRST_NAME_EMPTY],
    ['Last Name', { lastName: '' }, LAST_NAME_EMPTY],
    ['User ID', { userId: '' }, USER_ID_EMPTY],
    ['Password', { password: '' }, PASSWORD_EMPTY],
    ['User Type', { userType: '' }, USER_TYPE_EMPTY],
  ];

  test.each(cases)('FR-UA-3: a blank %s is refused verbatim', async (field, overrides, expected) => {
    const backend = stubBackend();
    backend.post(USERS_URL, { status: 400, body: { message: expected } });

    renderScreen(<AddUserPage />);
    fill(overrides);
    enter();

    await waitFor(() => expect(errorLine()).toEqual(expected));
    expect(statusLine()).toEqual('');
  });
});

describe('CU01 Add User — write outcomes (FR-UA-4, FR-UA-5, FR-UA-6)', () => {
  test('FR-UA-4: a successful WRITE reports the id and clears the screen', async () => {
    const backend = stubBackend();
    backend.post(USERS_URL, { userId: 'USER0001', message: ADDED });

    renderScreen(<AddUserPage />);
    fill();
    enter();

    await waitFor(() => expect(statusLine()).toEqual(ADDED));
    expect(screen.getByLabelText('First Name:')).toHaveValue('');
    expect(screen.getByLabelText('Last Name:')).toHaveValue('');
    expect(screen.getByLabelText('User ID:')).toHaveValue('');
    expect(screen.getByLabelText('User Type:')).toHaveValue('');
    expect(errorLine()).toEqual('');
  });

  test('FR-UA-5: a duplicate key is refused verbatim and the input is kept', async () => {
    const backend = stubBackend();
    backend.post(USERS_URL, { status: 409, body: { message: ALREADY_EXIST } });

    renderScreen(<AddUserPage />);
    fill();
    enter();

    await waitFor(() => expect(errorLine()).toEqual(ALREADY_EXIST));
    expect(screen.getByLabelText('User ID:')).toHaveValue('USER0001');
  });

  test('FR-UA-6: any other WRITE failure falls back to the WHEN OTHER literal', async () => {
    const backend = stubBackend();
    // No body at all: the client falls back to COUSR01C's own WHEN OTHER text.
    backend.post(USERS_URL, { status: 500, body: null });

    renderScreen(<AddUserPage />);
    fill();
    enter();

    await waitFor(() => expect(errorLine()).toEqual(UNABLE_TO_ADD));
  });

  test('the five fields are posted as the map holds them', async () => {
    const backend = stubBackend();
    backend.post(USERS_URL, { userId: 'USER0001', message: ADDED });

    renderScreen(<AddUserPage />);
    fill();
    enter();

    await waitFor(() => expect(statusLine()).toEqual(ADDED));
    expect(backend.callsTo('POST', USERS_URL)[0].body).toEqual({
      firstName: 'JOHN',
      lastName: 'DOE',
      userId: 'USER0001',
      password: 'PASS0001',
      userType: 'U',
    });
  });
});

describe('CU01 Add User — keys (FR-UA-7, FR-UA-8, FR-UA-9)', () => {
  test('FR-UA-8: PF4 wipes the fields and both message lines', async () => {
    const backend = stubBackend();
    backend.post(USERS_URL, { status: 409, body: { message: ALREADY_EXIST } });

    renderScreen(<AddUserPage />);
    fill();
    enter();
    await waitFor(() => expect(errorLine()).toEqual(ALREADY_EXIST));

    fireEvent.keyDown(window, { key: 'F4' });

    expect(screen.getByLabelText('First Name:')).toHaveValue('');
    expect(errorLine()).toEqual('');
    expect(statusLine()).toEqual('');
  });

  test('FR-UA-7: PF3 returns to the admin menu', async () => {
    stubBackend();

    const view = renderScreen(<AddUserPage />);
    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/admin'));
  });

  test('FR-UA-9 (quirk Q8): PF12 has no branch, so it is the invalid-key arm', async () => {
    stubBackend();

    const view = renderScreen(<AddUserPage />);
    fireEvent.keyDown(window, { key: 'F12' });

    await waitFor(() => expect(errorLine()).toEqual(INVALID_KEY));
    expect(view.location.pathname).toEqual('/');
  });
});
