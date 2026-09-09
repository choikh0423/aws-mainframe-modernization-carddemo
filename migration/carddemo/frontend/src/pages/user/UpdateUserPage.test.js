/**
 * CU02 — Update User (COUSR02C, map COUSR2A).
 *
 * Oracle: `app/cbl/COUSR02C.cbl` (:148, :182-210 the field edits, :238-243 the
 * no-change arm, :334-346 the fetch outcomes, :368-386 the rewrite outcomes)
 * and
 * `docs/migration/streams/UserManagement/programs/COUSR02C_functional_requirement.md`.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import UpdateUserPage from './UpdateUserPage';
import { renderScreen, stubBackend } from '../../testing/screenHarness';

const USER_URL = '/api/admin/users/USER0003';

// COUSR02C literals, verbatim.
const FETCH_PROMPT = 'Press PF5 key to save your updates ...';
const NOT_FOUND = 'User ID NOT found...';
const UNABLE_TO_LOOKUP = 'Unable to lookup User...';
const UNABLE_TO_UPDATE = 'Unable to Update User...';
const PLEASE_MODIFY = 'Please modify to update ...';
const USER_ID_EMPTY = 'User ID can NOT be empty...';
const FIRST_NAME_EMPTY = 'First Name can NOT be empty...';
const UPDATED = 'User USER0003 has been updated ...';

const RECORD = {
  userId: 'USER0003',
  firstName: 'JOHN',
  lastName: 'DOE',
  password: 'PASS0003',
  userType: 'U',
};

function errorLine() {
  return screen.getByRole('alert').textContent;
}

function statusLine() {
  return screen.getByRole('status').textContent;
}

async function renderFetched(backend) {
  const view = renderScreen(<UpdateUserPage />, { route: '/admin/users/update?id=USER0003' });
  await waitFor(() => expect(statusLine()).toEqual(FETCH_PROMPT));
  return view;
}

describe('CU02 Update User — fetch (FR-UU-3, FR-UU-4)', () => {
  test('FR-UU-3: the selected id is fetched on entry and the map is armed for PF5', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);

    await renderFetched(backend);

    expect(screen.getByLabelText('First Name:')).toHaveValue('JOHN');
    expect(screen.getByLabelText('Last Name:')).toHaveValue('DOE');
    expect(screen.getByLabelText('User Type:')).toHaveValue('U');
    expect(errorLine()).toEqual('');
  });

  test('FR-UU-4: an unknown id is refused verbatim and nothing is painted', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, { status: 404, body: { message: NOT_FOUND } });

    renderScreen(<UpdateUserPage />, { route: '/admin/users/update?id=USER0003' });

    await waitFor(() => expect(errorLine()).toEqual(NOT_FOUND));
    expect(screen.getByLabelText('First Name:')).toHaveValue('');
    expect(statusLine()).toEqual('');
  });

  test('FR-UU-2: a blank User ID on ENTER is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users/', { status: 400, body: { message: USER_ID_EMPTY } });

    renderScreen(<UpdateUserPage />);
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Fetch' }));

    await waitFor(() => expect(errorLine()).toEqual(USER_ID_EMPTY));
  });

  test('FR-UU-5: a READ failure falls back to the WHEN OTHER literal', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, { status: 500, body: null });

    renderScreen(<UpdateUserPage />, { route: '/admin/users/update?id=USER0003' });

    await waitFor(() => expect(errorLine()).toEqual(UNABLE_TO_LOOKUP));
  });
});

describe('CU02 Update User — rewrite (FR-UU-7, FR-UU-8, FR-UU-9)', () => {
  test('FR-UU-7: PF5 rewrites the four data fields and reports the id', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);
    backend.put(USER_URL, { userId: 'USER0003', message: UPDATED });

    await renderFetched(backend);
    fireEvent.change(screen.getByLabelText('First Name:'), { target: { value: 'JANE' } });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(statusLine()).toEqual(UPDATED));
    expect(backend.callsTo('PUT', USER_URL)[0].body).toEqual({
      firstName: 'JANE',
      lastName: 'DOE',
      password: 'PASS0003',
      userType: 'U',
    });
  });

  test('FR-UU-8: an unchanged record is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);
    backend.put(USER_URL, { status: 400, body: { message: PLEASE_MODIFY } });

    await renderFetched(backend);
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(errorLine()).toEqual(PLEASE_MODIFY));
    expect(statusLine()).toEqual('');
  });

  test('FR-UU-6: a blanked data field is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);
    backend.put(USER_URL, { status: 400, body: { message: FIRST_NAME_EMPTY } });

    await renderFetched(backend);
    fireEvent.change(screen.getByLabelText('First Name:'), { target: { value: '' } });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(errorLine()).toEqual(FIRST_NAME_EMPTY));
  });

  test('FR-UU-9: a REWRITE failure falls back to the WHEN OTHER literal', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);
    backend.put(USER_URL, { status: 500, body: null });

    await renderFetched(backend);
    fireEvent.change(screen.getByLabelText('Last Name:'), { target: { value: 'ROE' } });
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(errorLine()).toEqual(UNABLE_TO_UPDATE));
  });
});

describe('CU02 Update User — keys (FR-UU-12, FR-UU-13, FR-UU-14)', () => {
  test('FR-UU-12 (quirk Q4): PF3 saves first, then leaves for the admin menu', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);
    backend.put(USER_URL, { userId: 'USER0003', message: UPDATED });

    const view = await renderFetched(backend);
    fireEvent.change(screen.getByLabelText('Last Name:'), { target: { value: 'ROE' } });
    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/admin'));
    expect(backend.callsTo('PUT', USER_URL)).toHaveLength(1);
  });

  test('FR-UU-13: PF4 clears the id, the data fields and the messages', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);

    await renderFetched(backend);
    fireEvent.keyDown(window, { key: 'F4' });

    expect(screen.getByLabelText('Enter User ID:')).toHaveValue('');
    expect(screen.getByLabelText('First Name:')).toHaveValue('');
    expect(statusLine()).toEqual('');
    expect(errorLine()).toEqual('');
  });

  test('FR-UU-14: PF12 cancels to the admin menu without a rewrite', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);

    const view = await renderFetched(backend);
    fireEvent.change(screen.getByLabelText('Last Name:'), { target: { value: 'ROE' } });
    fireEvent.keyDown(window, { key: 'F12' });

    await waitFor(() => expect(view.location.pathname).toEqual('/admin'));
    expect(backend.callsTo('PUT', USER_URL)).toHaveLength(0);
  });
});
