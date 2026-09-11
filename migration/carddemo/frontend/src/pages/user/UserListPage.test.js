/**
 * CU00 — List Users (COUSR00C, map COUSR0A).
 *
 * Oracle: `app/cbl/COUSR00C.cbl` and
 * `docs/migration/streams/UserManagement/programs/COUSR00C_functional_requirement.md`
 * (FR-UL-1…FR-UL-16). Every message literal below is the COBOL `WS-MESSAGE`
 * text character for character, including the trailing `...`.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import UserListPage from './UserListPage';
import { renderScreen, stubBackend, currentUrl } from '../../testing/screenHarness';

// COUSR00C.cbl:249-254, :271-276, :600-607, :634-641, :668-675, CSMSG01Y.cpy:21-22.
const ALREADY_TOP = 'You are already at the top of the page...';
const ALREADY_BOTTOM = 'You are already at the bottom of the page...';
const AT_TOP = 'You are at the top of the page...';
const REACHED_BOTTOM = 'You have reached the bottom of the page...';
const REACHED_TOP = 'You have reached the top of the page...';
const INVALID_SELECTION = 'Invalid selection. Valid values are U and D';
const INVALID_KEY = 'Invalid key pressed. Please see below...';

function user(n) {
  const id = `USER${String(n).padStart(4, '0')}`;
  return { userId: id, firstName: `First${n}`, lastName: `Last${n}`, userType: 'U' };
}

function pageOf(from, count, { hasNextPage = true, hasPrevPage = false } = {}) {
  const rows = Array.from({ length: count }, (unused, i) => user(from + i));
  return {
    rows,
    count,
    firstId: rows.length ? rows[0].userId : null,
    lastId: rows.length ? rows[rows.length - 1].userId : null,
    hasNextPage,
    hasPrevPage,
  };
}

const FIRST_PAGE = pageOf(1, 10, { hasNextPage: true, hasPrevPage: false });
const SECOND_PAGE = pageOf(11, 10, { hasNextPage: false, hasPrevPage: true });

function message() {
  return screen.getByRole('alert').textContent;
}

async function renderList(backend) {
  const view = renderScreen(<UserListPage />);
  await screen.findByText('USER0001');
  expect(backend.callsTo('GET', '/api/admin/users')).toHaveLength(1);
  return view;
}

describe('CU00 List Users — pagination (FR-UL-1…FR-UL-8, FR-UL-14)', () => {
  test('FR-UL-1: the first entry browses from the top and shows page 1', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', FIRST_PAGE);

    await renderList(backend);

    expect(backend.callsTo('GET', '/api/admin/users')[0].url).toEqual('/api/admin/users');
    expect(screen.getAllByRole('row')).toHaveLength(11); // header + 10 rows
    expect(screen.getByText('Page: 1')).toBeInTheDocument();
  });

  test('FR-UL-3/FR-UL-14: PF8 browses from the last id shown and counts the page up', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', [FIRST_PAGE, SECOND_PAGE]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await screen.findByText('Page: 2');
    const requests = backend.callsTo('GET', '/api/admin/users');
    expect(requests).toHaveLength(2);
    expect(requests[1].url).toEqual('/api/admin/users?startId=USER0010&dir=next');
  });

  test('FR-UL-4/FR-UL-14: PF7 browses back from the first id shown, floor 1', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', [FIRST_PAGE, SECOND_PAGE, FIRST_PAGE]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });
    await screen.findByText('Page: 2');
    fireEvent.keyDown(window, { key: 'F7' });

    await screen.findByText('Page: 1');
    const requests = backend.callsTo('GET', '/api/admin/users');
    expect(requests).toHaveLength(3);
    expect(requests[2].url).toEqual('/api/admin/users?startId=USER0011&dir=prev');
  });

  test('FR-UL-5: PF7 on page 1 leaves the page alone and says so verbatim', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', FIRST_PAGE);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F7' });

    await waitFor(() => expect(message()).toEqual(ALREADY_TOP));
    expect(backend.callsTo('GET', '/api/admin/users')).toHaveLength(1);
    expect(screen.getByText('Page: 1')).toBeInTheDocument();
  });

  test('FR-UL-6: PF8 with no further record leaves the page alone and says so verbatim', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', pageOf(1, 10, { hasNextPage: false }));

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await waitFor(() => expect(message()).toEqual(ALREADY_BOTTOM));
    expect(backend.callsTo('GET', '/api/admin/users')).toHaveLength(1);
  });

  test('FR-UL-7: an empty browse shows the top-of-page literal', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', pageOf(1, 0, { hasNextPage: false }));

    renderScreen(<UserListPage />);

    await waitFor(() => expect(message()).toEqual(AT_TOP));
  });

  test('FR-UL-8: a short forward page ends at the file boundary', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', [FIRST_PAGE, pageOf(11, 4, { hasNextPage: false, hasPrevPage: true })]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await waitFor(() => expect(message()).toEqual(REACHED_BOTTOM));
  });

  test('FR-UL-8: a short backward page ends at the file boundary', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', [
      FIRST_PAGE,
      pageOf(11, 10, { hasNextPage: true, hasPrevPage: true }),
      pageOf(1, 4, { hasNextPage: true, hasPrevPage: false }),
    ]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });
    await screen.findByText('Page: 2');
    fireEvent.keyDown(window, { key: 'F7' });

    await waitFor(() => expect(message()).toEqual(REACHED_TOP));
  });

  /**
   * PROCESS-ENTER-KEY MOVEs 0 to CDEMO-CU00-PAGE-NUM before browsing from the
   * search key (cbl:227), and PROCESS-PF7-KEY guards on that counter rather
   * than on the file (cbl:248-254): a search result is page 1 even though the
   * file holds lower ids, so PF7 holds it there.
   */
  test('FR-UL-5: PF7 after a search stays on the result page', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', [
      FIRST_PAGE,
      pageOf(3, 10, { hasNextPage: true, hasPrevPage: true }),
    ]);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('Search User ID:'), { target: { value: 'USER0003' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Continue' }));
    await screen.findByText('USER0012');

    fireEvent.keyDown(window, { key: 'F7' });

    await waitFor(() => expect(message()).toEqual(ALREADY_TOP));
    expect(backend.callsTo('GET', '/api/admin/users')).toHaveLength(2);
    expect(screen.getByText('Page: 1')).toBeInTheDocument();
  });

  /**
   * The lookahead READNEXT/READPREV (cbl:308-315, :362-372) runs once the page
   * is full and sets WS-MESSAGE on ENDFILE; SEND-USRLST-SCREEN (:526) never
   * clears it, so a boundary page of exactly ten rows is not silent.
   */
  test('FR-UL-8: a last page of exactly ten rows still shows the boundary literal', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', [FIRST_PAGE, SECOND_PAGE]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await screen.findByText('USER0011');
    expect(message()).toEqual(REACHED_BOTTOM);
  });

  test('FR-UL-8: a first page of exactly ten rows with nothing after it shows it too', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', pageOf(1, 10, { hasNextPage: false }));

    await renderList(backend);

    expect(message()).toEqual(REACHED_BOTTOM);
  });

  test('FR-UL-8: paging back onto a full page 1 shows the top-of-file literal', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', [
      FIRST_PAGE,
      pageOf(11, 10, { hasNextPage: true, hasPrevPage: true }),
      FIRST_PAGE,
    ]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });
    await screen.findByText('USER0011');
    fireEvent.keyDown(window, { key: 'F7' });

    await screen.findByText('USER0001');
    expect(message()).toEqual(REACHED_TOP);
  });

  test('FR-UL-8: a full page with records on both sides stays silent', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', [
      FIRST_PAGE,
      pageOf(11, 10, { hasNextPage: true, hasPrevPage: true }),
    ]);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F8' });

    await screen.findByText('USER0011');
    expect(message()).toEqual('');
  });
});

describe('CU00 List Users — selection flags (FR-UL-9…FR-UL-13)', () => {
  test('FR-UL-9: `U` hands the row to CU02 Update', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', FIRST_PAGE);

    const view = await renderList(backend);
    fireEvent.change(screen.getByLabelText('select USER0003'), { target: { value: 'U' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Continue' }));

    await waitFor(() => expect(currentUrl(view.location))
      .toEqual('/admin/users/update?id=USER0003&from=CU00'));
  });

  test('FR-UL-10: `D` hands the row to CU03 Delete, and the flag is case-insensitive', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', FIRST_PAGE);

    const view = await renderList(backend);
    fireEvent.change(screen.getByLabelText('select USER0005'), { target: { value: 'd' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Continue' }));

    await waitFor(() => expect(currentUrl(view.location))
      .toEqual('/admin/users/delete?id=USER0005&from=CU00'));
  });

  test('FR-UL-11/FR-UL-13: any other flag is refused verbatim over a re-listed page 1', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', FIRST_PAGE);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('select USER0002'), { target: { value: 'X' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Continue' }));

    await waitFor(() => expect(message()).toEqual(INVALID_SELECTION));
    // FR-UL-13 (quirk Q9): the browse still restarts at page 1.
    expect(backend.callsTo('GET', '/api/admin/users')).toHaveLength(2);
    expect(screen.getByText('Page: 1')).toBeInTheDocument();
  });

  test('FR-UL-12 (quirk Q8): only the topmost non-blank flag is acted on', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', FIRST_PAGE);

    const view = await renderList(backend);
    fireEvent.change(screen.getByLabelText('select USER0004'), { target: { value: 'D' } });
    fireEvent.change(screen.getByLabelText('select USER0006'), { target: { value: 'U' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Continue' }));

    await waitFor(() => expect(currentUrl(view.location))
      .toEqual('/admin/users/delete?id=USER0004&from=CU00'));
  });

  /**
   * The Sel flags are input fields on the same 3270 map, so the ENTER AID is
   * sent from whichever of them holds the cursor (cbl:161-231).
   */
  test('FR-UL-9: ENTER pressed in a Sel field submits the screen', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', FIRST_PAGE);

    const view = await renderList(backend);
    const sel = screen.getByLabelText('select USER0003');
    fireEvent.change(sel, { target: { value: 'U' } });
    fireEvent.submit(sel);

    await waitFor(() => expect(currentUrl(view.location))
      .toEqual('/admin/users/update?id=USER0003&from=CU00'));
  });

  test('FR-UL-2: with no flag ENTER restarts the browse at the search key', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', FIRST_PAGE);

    await renderList(backend);
    fireEvent.change(screen.getByLabelText('Search User ID:'), { target: { value: 'USER0007' } });
    fireEvent.click(screen.getByRole('button', { name: 'ENTER — Continue' }));

    await waitFor(() => expect(backend.callsTo('GET', '/api/admin/users')).toHaveLength(2));
    expect(backend.callsTo('GET', '/api/admin/users')[1].url)
      .toEqual('/api/admin/users?startId=USER0007');
  });
});

describe('CU00 List Users — keys (FR-UL-15, FR-UL-16)', () => {
  test('FR-UL-15: PF3 returns to the admin menu', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', FIRST_PAGE);

    const view = await renderList(backend);
    fireEvent.keyDown(window, { key: 'F3' });

    await waitFor(() => expect(view.location.pathname).toEqual('/admin'));
  });

  test('FR-UL-16: any other AID is refused verbatim', async () => {
    const backend = stubBackend();
    backend.get('/api/admin/users', FIRST_PAGE);

    await renderList(backend);
    fireEvent.keyDown(window, { key: 'F5' });

    await waitFor(() => expect(message()).toEqual(INVALID_KEY));
  });
});
