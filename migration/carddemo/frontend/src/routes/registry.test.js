import { SCREENS, pathForProgram, screenForProgram } from './registry';

test('COSTM00C (Main Menu option 12) is the S-21 Statement List at /statements', () => {
  expect(pathForProgram('COSTM00C')).toBe('/statements');
  expect(screenForProgram('COSTM00C')).toMatchObject({
    title: 'Statement List',
    stream: 'S-21 OnlineStatements',
  });
});

test('COSTM01C is the S-21 Statement View at /statements/view', () => {
  expect(pathForProgram('COSTM01C')).toBe('/statements/view');
  expect(screenForProgram('COSTM01C')).toMatchObject({
    title: 'Statement View',
    stream: 'S-21 OnlineStatements',
  });
});

test('programs and paths stay unique', () => {
  const programs = SCREENS.map((s) => s.program);
  const paths = SCREENS.map((s) => s.path);
  expect(new Set(programs).size).toBe(programs.length);
  expect(new Set(paths).size).toBe(paths.length);
});
