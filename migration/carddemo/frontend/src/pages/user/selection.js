/**
 * COUSR00C PROCESS-ENTER-KEY row-selection scan (cbl:149-232), extracted so the
 * behaviour it encodes — including two quirks — is unit-testable.
 *
 * The COBOL walks the ten USRSEL fields top-down inside one EVALUATE per row and
 * moves the row's id into CDEMO-CU00-USR-SELECTED as soon as it finds a
 * non-blank flag; every later row is ignored (quirk Q8, "first selection wins", FR-UL-12).
 * 'U'/'u' hands off to COUSR02C, 'D'/'d' to COUSR03C, and any other non-blank
 * character produces "Invalid selection. Valid values are U and D".
 */

export const INVALID_SELECTION = 'Invalid selection. Valid values are U and D';

/**
 * @param rows       the page's rows, in display order (each with a userId)
 * @param selections map of userId -> the Sel character typed on that line
 * @returns {{action: 'update'|'delete'|'invalid'|'none', userId: string|null}}
 */
export function resolveSelection(rows, selections) {
  for (const row of rows) {
    const flag = ((selections || {})[row.userId] || '').trim();
    if (flag === '') {
      continue;
    }
    if (flag === 'U' || flag === 'u') {
      return { action: 'update', userId: row.userId };
    }
    if (flag === 'D' || flag === 'd') {
      return { action: 'delete', userId: row.userId };
    }
    return { action: 'invalid', userId: row.userId };
  }
  return { action: 'none', userId: null };
}
