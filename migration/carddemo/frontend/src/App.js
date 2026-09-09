import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { SCREENS } from './routes/registry';
import PlaceholderPage from './pages/PlaceholderPage';
import RequireSignOn from './auth/RequireSignOn';

/**
 * The consolidated CardDemo shell. Every route comes from the registry, so a
 * stream session adds a screen there and never edits this file. COSGN00C is the
 * only screen reachable without a COMMAREA-backed session; everything else
 * sits behind RequireSignOn, matching the legacy "sign on first" flow.
 */
function App() {
  return (
    <Routes>
      {SCREENS.map((screen) => {
        const element = screen.element || <PlaceholderPage screen={screen} />;
        return (
          <Route
            key={screen.program}
            path={screen.path}
            element={
              screen.program === 'COSGN00C' ? element : <RequireSignOn>{element}</RequireSignOn>
            }
          />
        );
      })}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
