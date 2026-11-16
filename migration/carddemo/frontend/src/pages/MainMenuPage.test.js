/**
 * CM00 — Main Menu (COMEN01C, map COMEN01) with the modern-only option 12.
 *
 * Oracle: `app/cpy/COMEN02Y.cpy` for options 1-11 (verbatim, "NN. Name" padded
 * into the map's X(40) OPTN fields) and COMEN01C PROCESS-ENTER-KEY for the
 * invalid-option literal. Option 12 "Statements" (COSTM00C, S-21
 * OnlineStatements) is a modernization extension and must route to /statements.
 */
import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import MainMenuPage from './MainMenuPage';
import { pathForProgram } from '../routes/registry';
import { renderScreen, stubBackend, currentUrl } from '../testing/screenHarness';

const LEGACY_OPTIONS = [
  [1, 'Account View', 'COACTVWC'],
  [2, 'Account Update', 'COACTUPC'],
  [3, 'Credit Card List', 'COCRDLIC'],
  [4, 'Credit Card View', 'COCRDSLC'],
  [5, 'Credit Card Update', 'COCRDUPC'],
  [6, 'Transaction List', 'COTRN00C'],
  [7, 'Transaction View', 'COTRN01C'],
  [8, 'Transaction Add', 'COTRN02C'],
  [9, 'Transaction Reports', 'CORPT00C'],
  [10, 'Bill Payment', 'COBIL00C'],
  [11, 'Pending Authorization View', 'COPAUS0C'],
];
const OPTIONS = [...LEGACY_OPTIONS, [12, 'Statements', 'COSTM00C']];

// COMEN01C.cbl — verbatim.
const INVALID_OPTION = 'Please enter a valid option number...';

/** STRING CDEMO-MENU-OPT-NUM '. ' CDEMO-MENU-OPT-NAME into the X(40) OPTN field. */
function displayText(number, name) {
  return `${String(number).padStart(2, '0')}. ${name}`.padEnd(40, ' ');
}

const MAIN_MENU = {
  tranId: 'CM00',
  programName: 'COMEN01C',
  title: 'Main Menu',
  prompt: 'Please select an option :',
  optionLength: 2,
  pfKeys: 'ENTER=Continue  F3=Exit',
  options: OPTIONS.map(([number, name, programName]) => ({
    number, name, programName, userType: 'U', displayText: displayText(number, name),
  })),
};

/** COMEN01C PROCESS-ENTER-KEY as the backend answers it. */
function select(call) {
  const echo = String(call.body.option).trim().padStart(2, '0');
  const option = OPTIONS.find(([number]) => String(number).padStart(2, '0') === echo);
  if (!option) {
    return {
      accepted: false, message: INVALID_OPTION, messageColour: 'RED', optionEcho: echo,
    };
  }
  const [number, name, programName] = option;
  return {
    accepted: true,
    optionNumber: number,
    optionName: name,
    programName,
    message: '',
    messageColour: 'GREEN',
    optionEcho: echo,
  };
}

let backend;

beforeEach(() => {
  backend = stubBackend()
    .get('/api/auth/session', { userId: 'USER0001', userType: 'U' })
    .get('/api/menu/main', MAIN_MENU)
    .post('/api/menu/main/select', select);
});

async function renderMenu() {
  const view = renderScreen(<MainMenuPage />, { route: '/menu' });
  await screen.findByText(/^12\. Statements/);
  return view;
}

async function choose(option) {
  fireEvent.change(screen.getByLabelText('Please select an option :'), {
    target: { value: option },
  });
  fireEvent.click(screen.getByRole('button', { name: /ENTER/ }));
  await waitFor(() => expect(backend.callsTo('POST', '/api/menu/main/select')).not.toHaveLength(0));
}

test('renders twelve options: COMEN02Y 1-11 verbatim, then 12. Statements', async () => {
  await renderMenu();
  const lines = screen.getAllByRole('listitem').map((li) => li.textContent);
  expect(lines).toHaveLength(12);
  expect(lines.slice(0, 11)).toEqual(LEGACY_OPTIONS.map(([n, name]) => displayText(n, name)));
  expect(lines[11]).toBe(`12. Statements${' '.repeat(26)}`);
});

test('option 12 navigates to /statements', async () => {
  const { location } = await renderMenu();
  await choose('12');
  expect(backend.callsTo('POST', '/api/menu/main/select')[0].body.option).toBe('12');
  await waitFor(() => expect(currentUrl(location)).toBe('/statements'));
});

test.each(LEGACY_OPTIONS.map(([n, , program]) => [n, program]))(
  'legacy option %i still routes to %s',
  async (number, program) => {
    const { location } = await renderMenu();
    await choose(String(number));
    await waitFor(() => expect(currentUrl(location)).toBe(pathForProgram(program)));
  },
);

test('13 is still rejected with the legacy invalid-option message', async () => {
  const { location } = await renderMenu();
  await choose('13');
  await waitFor(() => expect(screen.getByRole('alert').textContent).toBe(INVALID_OPTION));
  expect(screen.getByLabelText('Please select an option :').value).toBe('13');
  expect(currentUrl(location)).toBe('/menu');
});
