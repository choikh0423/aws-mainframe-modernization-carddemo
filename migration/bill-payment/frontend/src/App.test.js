import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import App from './App';

const base = {
  tranId: 'CB00', programName: 'COBIL00C', currentDate: '11/09/26', currentTime: '20:00:00',
  acctId: '', currentBalance: '', confirm: '', message: '', messageColor: 'RED',
  cursor: 'ACTIDIN', nextProgram: null, transactionId: null,
};

test('renders the BMS labels and sends ENTER with the screen fields', async () => {
  global.fetch = jest.fn()
    .mockResolvedValueOnce({ ok: true, json: async () => base })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ ...base, acctId: '00000000001',
      currentBalance: '+0000000194.00', message: 'Confirm to make a bill payment...', cursor: 'CONFIRM' }) });
  render(<App />);
  expect(await screen.findByText('Bill Payment')).toBeInTheDocument();
  expect(screen.getByText('ENTER=Continue  F3=Back  F4=Clear', { normalizer: (s) => s })).toBeInTheDocument();
  fireEvent.change(screen.getByTestId('acct-id'), { target: { value: '00000000001' } });
  fireEvent.click(screen.getByText('Enter'));
  await waitFor(() => expect(screen.getByTestId('message')).toHaveTextContent('Confirm to make a bill payment...'));
  const body = JSON.parse(global.fetch.mock.calls[1][1].body);
  expect(body).toMatchObject({ aid: 'ENTER', acctId: '00000000001', fromProgram: 'COMEN01C' });
  expect(screen.getByTestId('current-balance')).toHaveTextContent('+0000000194.00');
});
