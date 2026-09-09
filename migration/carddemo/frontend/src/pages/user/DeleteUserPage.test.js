/**
 * CU03 — Delete User (COUSR03C, map COUSR3A).
 *
 * Oracle: `app/cbl/COUSR03C.cbl` (:147 and :179 the empty-id edit, :281-296 the
 * fetch outcomes, :313-332 the delete outcomes) and
 * `docs/migration/streams/UserManagement/programs/COUSR03C_functional_requirement.md`.
 *
 * Quirk Q3: COUSR03C's WHEN OTHER arm on the DELETE keeps the *update* wording,
 * `Unable to Update User...`, and the migrated client keeps that quirk.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import DeleteUserPage from './DeleteUserPage';
import { renderScreen, stubBackend } from '../../testing/screenHarness';

const USER_URL = '/api/admin/users/USER0005';

// COUSR03C literals, verbatim.
const DELETE_PROMPT = 'Press PF5 key to delete this user ...';
const NOT_FOUND = 'User ID NOT found...';
const UNABLE_TO_LOOKUP = 'Unable to lookup User...';
const UNABLE_TO_UPDATE = 'Unable to Update User...';
const USER_ID_EMPTY = 'User ID can NOT be empty...';
const DELETED = 'User USER0005 has been deleted ...';

const RECORD = {
  userId: 'USER0005',
  firstName: 'JANE',
  lastName: 'ROE',
  userType: 'A',
};

function errorLine() {
  return screen.getByRole('alert').textContent;
}

function statusLine() {
  return screen.getByRole('status').textContent;
}

async function renderFetched(backend) {
  const view = renderScreen(<DeleteUserPage />, { route: '/admin/users/delete?id=USER0005' });
  await waitFor(() => expect(statusLine()).toEqual(DELETE_PROMPT));
  return view;
}

describe('CU03 Delete User — fetch (FR-UD-2, FR-UD-3, FR-UD-4)', () => {
  test('FR-UD-3: the selected id is painted read-only and armed for PF5', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);

    await renderFetched(backend);

    expect(screen.getByLabelText('First Name:')).toHaveValue('JANE');
    expect(screen.getByLabelText('First Name:')).toHaveAttribute('readonly');
    expect(screen.getByLabelText('Last Name:')).toHaveAttribute('readonly');
    expect(screen.getByLabelText('User Type:')).toHaveAttribute('readonly');
  });

  test('FR-UD-4: an unknown id is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, { status: 404, body: { message: NOT_FOUND } });

    renderScreen(<DeleteUserPage />, { route: '/admin/users/delete?id=USER0005' });

    await waitFor(() => expect(errorLine()).toEqual(NOT_FOUND));
    expect(screen.getByLabelText('First Name:')).toHaveValue('');
  });

  test('FR-UD-2: a blank User ID on ENTER is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users/', { status: 400, body: { message: USER_ID_EMPTY } });

    renderScreen(<DeleteUserPage />);
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Fetch' }));

    await waitFor(() => expect(errorLine()).toEqual(USER_ID_EMPTY));
  });

  test('FR-UD-5: a READ failure falls back to the WHEN OTHER literal', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, { status: 500, body: null });

    renderScreen(<DeleteUserPage />, { route: '/admin/users/delete?id=USER0005' });

    await waitFor(() => expect(errorLine()).toEqual(UNABLE_TO_LOOKUP));
  });
});

describe('CU03 Delete User — delete (FR-UD-7, FR-UD-8, FR-UD-14)', () => {
  test('FR-UD-7/FR-UD-14 (quirk Q7): PF5 deletes at once, with no confirmation', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);
    backend.delete(USER_URL, { userId: 'USER0005', message: DELETED });

    await renderFetched(backend);
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(statusLine()).toEqual(DELETED));
    expect(backend.callsTo('DELETE', USER_URL)).toHaveLength(1);
    // INITIALIZE-ALL-FIELDS after the DELETE (cbl:313-316).
    expect(screen.getByLabelText('Enter User ID:')).toHaveValue('');
    expect(screen.getByLabelText('First Name:')).toHaveValue('');
  });

  test('FR-UD-8: a vanished record is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);
    backend.delete(USER_URL, { status: 404, body: { message: NOT_FOUND } });

    await renderFetched(backend);
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(errorLine()).toEqual(NOT_FOUND));
  });

  test('quirk Q3: a DELETE failure keeps the update wording', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);
    backend.delete(USER_URL, { status: 500, body: null });

    await renderFetched(backend);
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(errorLine()).toEqual(UNABLE_TO_UPDATE));
  });
});

describe('CU03 Delete User — keys (FR-UD-11, FR-UD-12, FR-UD-13)', () => {
  test('FR-UD-12: PF4 clears the screen', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);

    await renderFetched(backend);
    fireEvent.keyDown(window, { key: 'F4' });

    expect(screen.getByLabelText('Enter User ID:')).toHaveValue('');
    expect(statusLine()).toEqual('');
  });

  test('FR-UD-11: PF3 returns to the admin menu', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);

    const view = await renderFetched(backend);
    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/admin'));
    expect(backend.callsTo('DELETE', USER_URL)).toHaveLength(0);
  });

  test('FR-UD-13: PF12 returns to the admin menu without deleting', async () => {
    const backend = stubBackend();
    backend.get(USER_URL, RECORD);

    const view = await renderFetched(backend);
    fireEvent.keyDown(window, { key: 'F12' });

    await waitFor(() => expect(view.location.pathname).toEqual('/admin'));
    expect(backend.callsTo('DELETE', USER_URL)).toHaveLength(0);
  });
});
