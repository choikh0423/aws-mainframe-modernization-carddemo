import { resolveSelection, INVALID_SELECTION } from './selection';

/**
 * FR-UL-9..FR-UL-13 — the COUSR00C row-selection scan and its quirks.
 */
const rows = [
  { userId: 'ADMIN001' },
  { userId: 'ADMIN002' },
  { userId: 'USER0001' },
];

test('FR-UL-9: `U` selects the row for the Update screen', () => {
  expect(resolveSelection(rows, { ADMIN002: 'U' }))
    .toEqual({ action: 'update', userId: 'ADMIN002' });
});

test('FR-UL-10: `D` selects the row for the Delete screen', () => {
  expect(resolveSelection(rows, { USER0001: 'D' }))
    .toEqual({ action: 'delete', userId: 'USER0001' });
});

test('FR-UL-9/FR-UL-10: the selection flag is case-insensitive', () => {
  expect(resolveSelection(rows, { ADMIN001: 'u' }).action).toEqual('update');
  expect(resolveSelection(rows, { ADMIN001: 'd' }).action).toEqual('delete');
});

test('FR-UL-11: any other non-blank flag is an invalid selection', () => {
  expect(resolveSelection(rows, { ADMIN001: 'X' }))
    .toEqual({ action: 'invalid', userId: 'ADMIN001' });
  expect(INVALID_SELECTION).toEqual('Invalid selection. Valid values are U and D');
});

test('FR-UL-12 (quirk Q8): only the first non-blank flag is honoured', () => {
  expect(resolveSelection(rows, { ADMIN002: 'D', USER0001: 'U' }))
    .toEqual({ action: 'delete', userId: 'ADMIN002' });
});

test('FR-UL-2: no flag at all leaves the browse to the search key', () => {
  expect(resolveSelection(rows, { ADMIN001: '  ' }))
    .toEqual({ action: 'none', userId: null });
});
