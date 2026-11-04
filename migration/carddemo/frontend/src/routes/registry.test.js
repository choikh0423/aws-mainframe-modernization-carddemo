import { pathForProgram, screenForProgram } from './registry';

describe('route registry', () => {
  test('main-menu option 12 (COSTM00C) routes to the Online Statements screen', () => {
    expect(pathForProgram('COSTM00C')).toBe('/statements');
    expect(screenForProgram('COSTM00C')).toMatchObject({
      tranId: 'CM00',
      title: 'Statement List',
      stream: 'S-21 OnlineStatements',
    });
  });

  test('the legacy main-menu targets keep their routes', () => {
    expect(pathForProgram('COACTVWC')).toBe('/accounts/view');
    expect(pathForProgram('COBIL00C')).toBe('/bill-payment');
    expect(pathForProgram('COPAUS0C')).toBe('/pending-authorizations');
  });
});
